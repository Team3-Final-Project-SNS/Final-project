package com.example.team3final.domain.post.service;

import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.repository.MatchRepository;
import com.example.team3final.domain.chat.service.ChatInternalService;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.post.event.PostVectorDeleteEvent;
import com.example.team3final.domain.post.event.PostVectorUpsertEvent;
import com.example.team3final.domain.post.repository.PostRepository;
import com.example.team3final.domain.user.service.UserInternalService;
import com.example.team3final.domain.user.service.UserPointService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Post 도메인의 관리자 제재 및 복구 처리를 담당하는 서비스
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostModerationServiceImpl implements PostModerationService {

    private final PostRepository postRepository;
    private final UserPointService userPointService;
    private final NotificationPublisher notificationPublisher;
    private final UserInternalService userInternalService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final MatchRepository matchRepository;
    private final ChatInternalService chatInternalService;
    private final RedisPostService redisPostService;

    // 게시글 강제 삭제 사유를 받아서 포인트 반환
    @Override
    public int forceDeletePost(Post post, String reason) {

        // 작성자에게 예치 포인트 전액 환불
        int refundedPoint = post.getAuthorDeposit();
        userPointService.refundAuthorDeposit(post.getAuthorId(), refundedPoint, post.getId());
        cancelAndRefundActiveApplicants(post);

        // 소프트 삭제 + 사유 영속화
        // post.delete(reason) 가 deleteReason 세팅 후 deletedAt=now() 세팅
        // @Transactional 더티 체킹으로 트랜잭션 종료 시 두 컬럼 모두 UPDATE
        post.deleteAndReason(reason);

        publishPostVectorDeleteEvent(post.getId());
        redisPostService.evictPostLists();

        // 41. 게시글 삭제 알림 - 게시글 작성자에게
        notificationPublisher.sendPostDeleted(
                post.getAuthorId(), // userId (Long)
                post.getId()        // postId (Long) -> 이 부분이 누락되었었습니다!
        );
        return refundedPoint;
    }

    // 강제 삭제 게시글 복구
    @Override
    @Transactional
    public int restorePost(Post post) {

        // 복구할 예치금 -> 삭제 당시 환불했던 책임비
        int redepositPoint = post.getAuthorDeposit();

        // 삭제 때 작성자에게 환불했으므로, 복구 시 다시 차감
        // 잔액 부족 시 user.deduct() 내부에서 예외 발생
        userPointService.redepositAuthorDeposit(post.getAuthorId(), redepositPoint, post.getId());
        // 게시글 복구
        post.restore();

        publishPostVectorUpsertEvent(post);
        redisPostService.evictPostLists();

        // 42. 게시글 복구 알림 - 게시글 작성자에게
        notificationPublisher.sendPostRestored(
                post.getAuthorId(), // userId (Long)
                post.getId()        // postId (Long) -> 엔티티 식별자 메서드명에 맞게 입력 (ex: post.getPostId())
        );
        notifyCancelledApplicantsPostRestored(post);

        return redepositPoint;
    }

    private List<Match> getActiveApplicantMatches(Post post) {
        return matchRepository.findAllByPostIdAndStatusOrderByIdAsc(post.getId(), MatchStatus.MATCHED);
    }

    private void cancelAndRefundActiveApplicants(Post post) {
        List<Match> activeMatches = getActiveApplicantMatches(post);

        for (Match match : activeMatches) {
            userPointService.refundApplicantDeposit(
                    match.getApplicantId(),
                    match.getApplicantDeposit(),
                    match.getId()
            );
            match.cancel();
            post.decreaseCurrentApplicants();
            notificationPublisher.sendApplicantPostDeleted(match.getApplicantId(), post.getId());
        }

        if (!activeMatches.isEmpty()) {
            chatInternalService.deactivateChatRoom(post.getId());
        }
    }

    private void notifyCancelledApplicantsPostRestored(Post post) {
        for (Match match : matchRepository.findAllByPostIdAndStatusOrderByIdAsc(post.getId(), MatchStatus.CANCELLED)) {
            notificationPublisher.sendApplicantPostRestored(match.getApplicantId(), post.getId());
        }
    }

    // 관리자 게시글 목록 조회, 전체 대학 조회와 특정 대학 필터 분기 처리
    @Override
    public Page<Post> getPostsForAdmin(
            List<Long> authorIds,
            PostStatus status,
            Boolean deleted,
            String keyword,
            Pageable pageable
    ) {
        if (authorIds == null) {
            return postRepository.findAllForAdmin(status, deleted, keyword, pageable);
        }
        return postRepository.findAllForAdminByAuthorIds(authorIds, status, deleted, keyword, pageable);
    }

    private void publishPostVectorUpsertEvent(Post post) {
        if (applicationEventPublisher == null || post == null || !post.isOpen() || post.isDeleted()) {
            return;
        }

        // 이벤트에는 추천 검색에 필요한 스냅샷만 담습니다.
        // Listener가 AFTER_COMMIT에서 embedding 생성과 PostgreSQL upsert를 비동기로 수행합니다.
        applicationEventPublisher.publishEvent(
                new PostVectorUpsertEvent(
                        post.getId(),
                        post.getAuthorId(),
                        userInternalService.findUserById(post.getAuthorId()).getUniversityId(),
                        post.getStatus(),
                        post.getMeetAt(),
                        post.getPlaceName(),
                        post.getContent(),
                        post.getAuthorDeposit(),
                        post.getMaxApplicants(),
                        post.getCurrentApplicants(),
                        post.getPlaceLat(),
                        post.getPlaceLng()
                )
        );
    }

    private void publishPostVectorDeleteEvent(Long postId) {
        if (applicationEventPublisher == null || postId == null) {
            return;
        }

        // 상태가 OPEN이 아니게 된 게시글은 postId만 알면 벡터 인덱스에서 제거할 수 있습니다.
        applicationEventPublisher.publishEvent(new PostVectorDeleteEvent(postId));
    }
}
