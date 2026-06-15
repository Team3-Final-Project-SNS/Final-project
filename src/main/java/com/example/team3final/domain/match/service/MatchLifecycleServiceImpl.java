package com.example.team3final.domain.match.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.MatchException;
import com.example.team3final.domain.chat.service.ChatInternalService;
import com.example.team3final.domain.location.service.UserLocationCleanupService;
import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.repository.MatchRepository;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.post.event.PostVectorDeleteEvent;
import com.example.team3final.domain.post.service.PostInternalService;
import com.example.team3final.domain.review.util.ReviewRedisZSetKeys;
import com.example.team3final.domain.user.service.UserPointService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

// Match 도메인의 생명주기 전환을 담당하는 서비스
// QR 인증 완료, 시스템 취소, Post 완료 처리처럼 매칭 진행 상태와 게시글 완료 상태가 함께 전환되는 흐름을 처리
@Service
@RequiredArgsConstructor
@Transactional
public class MatchLifecycleServiceImpl implements MatchLifecycleService {

    private final MatchRepository matchRepository;
    private final MatchInternalService matchInternalService;
    private final PostInternalService postInternalService;
    private final UserPointService userPointService;
    private final UserLocationCleanupService userLocationCleanupService;
    private final ChatInternalService chatInternalService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final StringRedisTemplate redisTemplate;

    // 시스템 취소 — QR 만료 시점까지 양측 모두 현장에 있었으나 QR 인증 미완료
    @Override
    public void cancelMatchBySystem(Long matchId) {

        Match match = matchInternalService.getMatchById(matchId);

        // 이미 종결된 건은 스킵 (스케줄러 중복 실행 방어)
        if (match.getStatus() != MatchStatus.MATCHED) {
            return;
        }

        // Match → CANCELLED
        match.cancel();

        Post post = postInternalService.getPostById(match.getPostId());

        // Post → CANCELLED
        post.cancel();

        // 취소된 게시글은 신청 가능한 모집글이 아니므로 벡터 추천 인덱스에서 제거합니다.
        publishPostVectorDeleteEvent(post.getId());

        // 양측 전액 환불 (둘 다 현장에 있었으므로 귀책 없음 → 패널티 없음)
        userPointService.refundAuthorDeposit(post.getAuthorId(), post.getAuthorDeposit(), post.getId());
        userPointService.refundApplicantDeposit(
                match.getApplicantId(),
                match.getApplicantDeposit(),
                matchId
        );

        // 위치 데이터 삭제 (개인정보 최소 수집 원칙)
        userLocationCleanupService.deleteLocationsByMatchId(matchId);

        // 채팅방 비활성화
        chatInternalService.deactivateChatRoom(match.getPostId());
    }

    // QR 스캔 성공 시 Match 단건만 COMPLETE 처리하는 메서드
    // 신청자 예치금 환급, Post 완료, 등록자 환급, 채팅방 비활성화 예약은 여기서 하지 않음
    @Override
    public boolean completeSingleMatch(Long matchId) {

        // 동일 Match에 대한 동시 QR 스캔을 막기 위해 PESSIMISTIC_WRITE 락으로 조회
        Match match = matchRepository.findByIdWithLock(matchId)
                .orElseThrow(() -> new MatchException(ErrorCode.MATCH_NOT_FOUND));

        // 이미 완료된 Match에 대해서 중복 QR 스캔 방어
        if (match.getStatus() == MatchStatus.COMPLETED) {
            return false;
        }

        // QR 정상 완료는 MATCHED 상태에서만 가능
        if (match.getStatus() != MatchStatus.MATCHED) {
            throw new MatchException(ErrorCode.MATCH_INVALID_STATUS);
        }

        // Match 엔티티의 도메인 메서드로 MATCHED -> COMPLETED 상태 전이
        match.complete();

        // 신청자 예치금 환급
        // Meet 도메인에서 하지 않고 Match 도메인에서 처리
        userPointService.refundApplicantDeposit(
                match.getApplicantId(),
                match.getApplicantDeposit(),
                match.getId()
        );

        // 정상 만남 완료 후 후기 작성 마지막 날 알림 예약
        // 기존 completeMatch()에서 하던 역할을 completeSingleMatch()에도 반영
        LocalDateTime reviewDeadlineReminderAt = match.getCompletedAt()
                .plusDays(7)
                .toLocalDate()
                .atTime(9, 0);

        // 후기 작성 마지막 날 오전 9시에 알림 발송되도록 ZSet에 예약
        redisTemplate.opsForZSet().add(
                ReviewRedisZSetKeys.DEADLINE_REMINDER,
                String.valueOf(match.getId()),
                reviewDeadlineReminderAt.toEpochSecond(ZoneOffset.ofHours(9))
        );

        // 아직 진행 중인 MATCHED가 남아 있으면 Post는 아직 완료하면 안 됨
        long remainingMatchedCount = matchRepository.countByPostIdAndStatus(match.getPostId(), MatchStatus.MATCHED);

        // 남은 MATCHED Match가 0개라면 이번 스캔이 해당 Post의 마지막 스캔
        return remainingMatchedCount == 0;
    }

    // 모든 활성 매칭이 종료된 뒤 Post 전체 COMPLETED 처리
    // Post COMPLETE 처리, 등록자 책임비 환급, 중복 호출되어도 한 번만 처리되도록 멱등성 보장
    @Override
    public void completePostIfAllMatchesCompleted(Long postId) {

        // Post 락을 먼저 잡아 정상 완료와 노쇼/이의제기 완료가 동시에 등록자 책임비를 정산하지 않게 한다.
        Post post = postInternalService.getPostByIdWithLock(postId);

        long remainingActiveCount = matchRepository.countByPostIdAndStatusIn(
                postId,
                List.of(MatchStatus.MATCHED, MatchStatus.DISPUTED)
        );

        // DISPUTED도 아직 결론이 나지 않은 활성 Match이므로 Post 완료를 보류한다.
        if (remainingActiveCount > 0) {
            return;
        }

        // 멱등성 보장 -> 이미 완료된 Post면 등록자 환급도 다시 하지 않음
        if (post.getStatus() == PostStatus.COMPLETED) {
            return;
        }

        // Post 엔티티의 도메인 메서드로 상태를 COMPLETED로 전환
        post.complete();

        // 식사가 완료된 게시글은 검색/추천 대상에서 제외합니다.
        publishPostVectorDeleteEvent(post.getId());

        // 등록자 책임비는 그룹 만남 전체가 정상 종료된 시점에 한 번만 환급
        if (!userPointService.hasAuthorDepositSettlement(post.getAuthorId(), post.getId())) {
            userPointService.refundAuthorDeposit(
                    post.getAuthorId(),
                    post.getAuthorDeposit(),
                    post.getId()
            );
        }
    }

    private void publishPostVectorDeleteEvent(Long postId) {
        if (applicationEventPublisher == null || postId == null) {
            return;
        }

        // 삭제 이벤트는 postId만 필요합니다. Listener가 커밋 이후 pgvector 테이블에서 해당 행을 제거합니다.
        applicationEventPublisher.publishEvent(new PostVectorDeleteEvent(postId));
    }
}
