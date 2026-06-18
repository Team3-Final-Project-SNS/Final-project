package com.example.team3final.domain.post.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.PostException;
import com.example.team3final.domain.chat.service.ChatInternalService;
import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.repository.MatchRepository;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.dto.request.CreatePostRequestDto;
import com.example.team3final.domain.post.dto.request.UpdatePostRequestDto;
import com.example.team3final.domain.post.dto.response.CreatePostResponseDto;
import com.example.team3final.domain.post.dto.response.DeletePostResponseDto;
import com.example.team3final.domain.post.dto.response.UpdatePostResponseDto;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.event.PostVectorDeleteEvent;
import com.example.team3final.domain.post.event.PostVectorUpsertEvent;
import com.example.team3final.domain.post.repository.PostRepository;
import com.example.team3final.domain.user.service.UserInternalService;
import com.example.team3final.domain.user.service.UserPointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.LocalDateTime;

// Post 도메인의 생성/수정/삭제 등 사용자 요청 기반 변경 작업을 담당하는 서비스
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PostCommandServiceImpl implements PostCommandService {

    private final PostRepository postRepository;
    private final PostInternalService postInternalService;
    private final UserPointService userPointService;
    private final UserInternalService userInternalService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final MatchRepository matchRepository;
    private final ChatInternalService chatInternalService;
    private final NotificationPublisher notificationPublisher;
    private final RedisPostService redisPostService;

    @Override
    public CreatePostResponseDto createPost(Long authorId, CreatePostRequestDto request) {

        // 만남 시간 검증
        if (request.getMeetAt().isBefore(LocalDateTime.now())) {
            throw new PostException(ErrorCode.POST_INVALID_MEET_AT);
        }

        // 책임비 검증 (1) 최소 200P 이상인지
        if (request.getAuthorDeposit() < Post.MIN_AUTHOR_DEPOSIT) {
            throw new PostException(ErrorCode.POST_INVALID_DEPOSIT);
        }

        // 책임비 검증 (2) 100P 단위인지 — BUG-07 수정
        // % 는 나머지 연산자 → 100으로 나눴을 때 나머지가 0이 아니면 단위 위반
        // ex) 150 % 100 = 50 → 예외 / 300 % 100 = 0 → 통과
        if (request.getAuthorDeposit() % 100 != 0) {
            throw new PostException(ErrorCode.POST_INVALID_DEPOSIT_UNIT);
        }

        // 2. 포인트 차감
        userPointService.deductPoint(authorId, request.getAuthorDeposit(), null);

        // 3. Post 엔티티 생성
        Post post = Post.builder()
                .authorId(authorId)
                .meetAt(request.getMeetAt())
                .placeName(request.getPlaceName())
                .placeLat(request.getPlaceLat())
                .placeLng(request.getPlaceLng())
                .content(request.getContent())
                .authorDeposit(request.getAuthorDeposit())
                .maxApplicants(request.getMaxApplicants()) // 최대 참여 인원 추가
                .build();

        // 4. 저장
        Post savedPost = postRepository.save(post);

        // OPEN 게시글은 매칭 AI 추천 후보가 될 수 있으므로 커밋 이후 벡터 인덱스에 반영합니다.
        publishPostVectorUpsertEvent(savedPost);
        redisPostService.evictPostLists();

        log.info("[Post] 게시글 생성 완료 - postId: {}, authorId: {}, status: {}, meetAt: {}",
                savedPost.getId(), authorId, savedPost.getStatus(), savedPost.getMeetAt());

        // 5. Response
        // userService.getUserInfo(authorId) → UserInfoDto 반환, 거기서 nickname() 추출
        String authorNickname = userInternalService.getUserInfo(authorId).nickname();

        return CreatePostResponseDto.from(savedPost, authorNickname);
    }

    @Override
    public UpdatePostResponseDto updatePost(Long postId, Long userId, UpdatePostRequestDto request) {

        // 1. 게시글 조회
        Post post = postInternalService.getPostById(postId);

        // 2. 작성자 본인 검증
        if (!post.isAuthor(userId)) {
            throw new PostException(ErrorCode.POST_NOT_AUTHOR);
        }

        // 3. 상태 검증 — OPEN만 수정 가능
        if (!post.isOpen()) {
            throw new PostException(ErrorCode.POST_NOT_OPEN);
        }

        // 4. authorDeposit 검증 + 차액 처리
        Integer newDeposit = request.getAuthorDeposit();

        if (newDeposit != null) {
            // 수정 시에도 동일한 100P 단위 검증 — BUG-07 수정
            if (newDeposit % 100 != 0) {
                throw new PostException(ErrorCode.POST_INVALID_DEPOSIT_UNIT);
            }

            // 차액 계산 — 양수면 추가 차감, 음수면 환불
            int oldDeposit = post.getAuthorDeposit();
            int diff = newDeposit - oldDeposit;

            if (hasActiveApplicants(post.getId()) && diff != 0) {
                throw new PostException(ErrorCode.POST_CONDITION_LOCKED);
            }

            if (diff > 0) {
                // 증액: diff만큼 추가 차감
                userPointService.deductEditDeposit(userId, diff);
            } else if (diff < 0) {
                // 감액: |diff|만큼 환불
                userPointService.refundEditDeposit(userId, Math.abs(diff));
            }
            // diff == 0이면 아무것도 안 함
        }

        // 5. 엔티티 update() 호출 — 상태/필드 변경은 도메인 메서드가 책임
        post.update(
                request.getMeetAt(),
                request.getPlaceName(),
                request.getPlaceLat(),
                request.getPlaceLng(),
                request.getContent(),
                request.getAuthorDeposit()
        );

        // 장소/한마디/시간/책임비가 바뀌면 의미 검색 결과와 필터 조건도 달라지므로 인덱스를 갱신합니다.
        publishPostVectorUpsertEvent(post);
        redisPostService.evictPostLists();

        // 6. 응답 DTO 변환
        return UpdatePostResponseDto.from(post);
    }

    @Override
    public DeletePostResponseDto deletePost(Long postId, Long userId) {

        // 1. 게시글 조회
        Post post = postInternalService.getPostById(postId);

        // 2. 작성자 본인 검증
        if (!post.isAuthor(userId)) {
            throw new PostException(ErrorCode.POST_NOT_AUTHOR);
        }

        // 3. 상태 검증 — OPEN만 삭제 가능
        if (!post.isOpen()) {
            throw new PostException(ErrorCode.POST_NOT_OPEN);
        }

        // 4. 삭제 시점에 활성 신청자가 있는지 먼저 확인
        // 활성 신청자가 없으면 단순 모집글 삭제이므로 등록자 책임비 전액 환불
        // 활성 신청자가 1명 이상 있으면 등록자가 모임을 파토낸 케이스이므로 등록자 책임비 50%만 환불
        List<Match> activeMatches = getActiveApplicantMatches(post);
        boolean hasMatchedApplicant = !activeMatches.isEmpty();
        int refundedPoint = hasMatchedApplicant
                ? post.getAuthorDeposit() / 2
                : post.getAuthorDeposit();

        // 5. 등록자 책임비 환불
        // 매칭된 신청자가 있으면 PARTIAL_REFUND로 50% 환불 이력을 남김
        if (hasMatchedApplicant) {
            userPointService.partialRefundAuthorDeposit(userId, post.getAuthorDeposit(), postId);
        } else {
            userPointService.refundAuthorDeposit(userId, post.getAuthorDeposit(), postId, "게시글 삭제 환불");
        }
        notificationPublisher.sendAuthorCancelledPost(userId, postId, hasMatchedApplicant);

        cancelActiveApplicantMatches(post, activeMatches);

        // 6. 게시글 소프트 삭제
        post.delete();

        // 소프트 삭제도 사용자에게 추천되면 안 되므로 벡터 인덱스에서는 물리 삭제합니다.
        publishPostVectorDeleteEvent(postId);
        redisPostService.evictPostLists();

        log.info("[Post] 게시글 삭제 처리 - postId: {}, authorId: {}, refundedPoint: {}",
                postId, userId, refundedPoint);

        return DeletePostResponseDto.of(postId, refundedPoint);
    }

    private boolean hasActiveApplicants(Long postId) {
        return matchRepository.countByPostIdAndStatus(postId, MatchStatus.MATCHED) > 0;
    }

    private List<Match> getActiveApplicantMatches(Post post) {
        return matchRepository.findAllByPostIdAndStatusOrderByIdAsc(
                post.getId(),
                MatchStatus.MATCHED
        );
    }

    private void cancelActiveApplicantMatches(Post post, List<Match> activeMatches) {
        for (Match match : activeMatches) {
            userPointService.refundApplicantDeposit(
                    match.getApplicantId(),
                    match.getApplicantDeposit(),
                    match.getId(),
                    "게시글 삭제 환불"
            );
            match.cancel();
            post.decreaseCurrentApplicants();
            notificationPublisher.sendHostCancelled(match.getApplicantId(), match.getId());
        }

        if (!activeMatches.isEmpty()) {
            chatInternalService.deactivateChatRoom(post.getId());
        }
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
