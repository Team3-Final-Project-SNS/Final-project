package com.example.team3final.domain.match.service;

import com.example.team3final.domain.chat.service.ChatInternalService;
import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.repository.MatchRepository;
import com.example.team3final.domain.meet.enums.VerificationStatus;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.post.event.PostVectorDeleteEvent;
import com.example.team3final.domain.post.service.PostInternalService;
import com.example.team3final.domain.post.service.PostLifecycleService;
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

// Match 도메인의 노쇼 및 이의제기 결과 반영을 담당하는 서비스
@Service
@RequiredArgsConstructor
@Transactional
public class MatchNoShowServiceImpl implements MatchNoShowService {

    private final MatchRepository matchRepository;
    private final MatchInternalService matchInternalService;
    private final PostInternalService postInternalService;
    private final PostLifecycleService postLifecycleService;
    private final UserPointService userPointService;
    private final ChatInternalService chatInternalService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void markDisputed(Long matchId) {

        Match match = matchInternalService.getMatchById(matchId);

        // MATCHED 상태일 때만 DISPUTED로 전환
        // DISPUTED 중인 건에 중복 이의제기가 들어와도 상태가 다시 바뀌지 않도록 방어
        if (match.getStatus() == MatchStatus.MATCHED) {
            match.dispute();
        }
    }

    // 관리자 ACCEPTED 판정
    // 노쇼가 아니라고 인정된 케이스다.
    // 따라서 해당 Match를 정상 완료 처리하고, 포인트도 정상 완료 기준으로 정산한다.
    // 신청자 예치금: 해당 Match 완료 시 전액 반환
    // 등록자 책임비: 같은 Post의 모든 활성 Match가 끝났을 때 1회 반환
    // 반환값 이의제기자에게 실제 반환된 포인트
    @Override
    public int completeSingleMatchByDispute(Long matchId, Long submitterId) {

        Match match = matchInternalService.getMatchById(matchId);

        // 관리자 판정 처리 가능한 상태가 아니면 중복 처리 방지
        if (!canResolveDispute(match)) {
            return 0;
        }

        Post post = postInternalService.getPostById(match.getPostId());

        boolean submitterIsAuthor = submitterId.equals(post.getAuthorId());
        boolean submitterIsApplicant = submitterId.equals(match.getApplicantId());

        int refundedPoint = 0;

        // Match 상태를 정상 완료로 전환
        match.completeByDispute();

        // 신청자 예치금 전액 반환
        userPointService.refundPoint(
                match.getApplicantId(),
                match.getApplicantDeposit(),
                match.getId()
        );

        if (submitterIsApplicant) {
            refundedPoint = match.getApplicantDeposit();
        }

        // 정상 완료 흐름과 동일하게 후기 작성 마지막 날 알림 예약
        LocalDateTime reviewDeadlineReminderAt = match.getCompletedAt()
                .plusDays(7)
                .toLocalDate()
                .atTime(9, 0);

        redisTemplate.opsForZSet().add(
                ReviewRedisZSetKeys.DEADLINE_REMINDER,
                String.valueOf(match.getId()),
                reviewDeadlineReminderAt.toEpochSecond(ZoneOffset.ofHours(9))
        );

        // 같은 Post에 아직 진행 중이거나 이의제기 중인 Match가 남아 있으면
        // 등록자 책임비는 아직 반환하지 않는다.
        boolean hasRemainingActiveMatch = matchRepository.findAllByPostId(match.getPostId())
                .stream()
                .anyMatch(this::canResolveDispute);

        if (hasRemainingActiveMatch) {
            return refundedPoint;
        }

        // 모든 활성 Match가 끝났다면 Post 완료 + 등록자 책임비 반환
        Post lockedPost = postInternalService.getPostByIdWithLock(match.getPostId());

        if (lockedPost.getStatus() != PostStatus.COMPLETED) {
            lockedPost.complete();

            // 관리자 이의제기 ACCEPTED로 게시글이 완료되는 경로도 추천 인덱스에서 제거합니다.
            // 보통 MATCHED 전환 때 이미 삭제되지만, 완료 지점에서도 한 번 더 삭제 이벤트를 발행해 정합성을 보강합니다.
            publishPostVectorDeleteEvent(lockedPost.getId());

            userPointService.refundPoint(
                    lockedPost.getAuthorId(),
                    lockedPost.getAuthorDeposit(),
                    lockedPost.getId()
            );

            if (submitterIsAuthor) {
                refundedPoint = lockedPost.getAuthorDeposit();
            }

            // 모든 매칭이 끝났으므로 채팅방 비활성화 예약
            chatInternalService.scheduleChatRoomDeactivation(match.getPostId());
        }

        return refundedPoint;
    }

    // 관리자 PARTIALLY_ACCEPTED 판정
    // 노쇼 자체는 맞지만, 이의제기자의 사유가 일부 인정된 케이스다.
    // - 이의제기자: 50% 반환
    // - 피해 상대방: 100% 반환
    // - 포인트 정산은 Match 도메인에서 처리한다.
    //
    // restoredStatus:
    // - HOST_NO_SHOW  → 등록자 노쇼. 그룹에서는 같은 Post의 활성 Match 전체가 등록자 노쇼 처리된다.
    // - GUEST_NO_SHOW → 해당 신청자 Match 하나만 신청자 노쇼 처리된다.
    // - BOTH_NO_SHOW  → 해당 Match 하나만 양측 노쇼 처리된다.
    //
    // 반환값:
    // - 이의제기자에게 실제 반환된 포인트
    @Override
    public int markNoShowByDispute(
            Long matchId,
            VerificationStatus restoredStatus,
            Long submitterId
    ) {

        Match match = matchInternalService.getMatchById(matchId);

        if (!canResolveDispute(match)) {
            return 0;
        }

        Post post = postInternalService.getPostById(match.getPostId());

        boolean submitterIsAuthor = submitterId.equals(post.getAuthorId());
        boolean submitterIsApplicant = submitterId.equals(match.getApplicantId());

        int refundedPoint = 0;

        if (restoredStatus == VerificationStatus.HOST_NO_SHOW) {

            // 등록자 노쇼는 Post 전체 책임이다.
            // 같은 Post의 MATCHED / DISPUTED 상태 Match 전체를 AUTHOR_NO_SHOW로 확정한다.
            List<Match> activeMatches = matchRepository.findAllByPostId(match.getPostId())
                    .stream()
                    .filter(this::canResolveDispute)
                    .toList();

            if (activeMatches.isEmpty()) {
                return 0;
            }

            // 등록자가 이의제기자라면 등록자 책임비 50% 반환
            if (submitterIsAuthor) {
                userPointService.partialRefundPoint(
                        post.getAuthorId(),
                        post.getAuthorDeposit(),
                        match.getId()
                );
                refundedPoint = post.getAuthorDeposit() / 2;
            }

            // 등록자 노쇼 피해자는 모든 활성 신청자다.
            // 모든 신청자 예치금은 전액 반환한다.
            for (Match activeMatch : activeMatches) {
                activeMatch.markNoShow(MatchStatus.AUTHOR_NO_SHOW);

                userPointService.refundPoint(
                        activeMatch.getApplicantId(),
                        activeMatch.getApplicantDeposit(),
                        activeMatch.getId()
                );

                if (submitterId.equals(activeMatch.getApplicantId())) {
                    refundedPoint = activeMatch.getApplicantDeposit();
                }
            }

            postLifecycleService.completePost(match.getPostId());
            return refundedPoint;
        }

        if (restoredStatus == VerificationStatus.GUEST_NO_SHOW) {

            // 신청자 노쇼는 해당 Match 하나만 확정
            match.markNoShow(MatchStatus.APPLICANT_NO_SHOW);

            if (submitterIsApplicant) {
                // 노쇼 당사자인 신청자의 사유가 일부 인정됨 → 50% 반환
                userPointService.partialRefundPoint(
                        match.getApplicantId(),
                        match.getApplicantDeposit(),
                        match.getId()
                );
                refundedPoint = match.getApplicantDeposit() / 2;
            }

            // 등록자는 피해자이므로 전액 반환
            userPointService.refundPoint(
                    post.getAuthorId(),
                    post.getAuthorDeposit(),
                    match.getId()
            );

            if (submitterIsAuthor) {
                refundedPoint = post.getAuthorDeposit();
            }

        } else if (restoredStatus == VerificationStatus.BOTH_NO_SHOW) {

            // 양측 노쇼는 해당 Match 하나만 확정
            match.markNoShow(MatchStatus.BOTH_NO_SHOW);

            // BOTH_NO_SHOW에서 PARTIALLY_ACCEPTED는 이의제기자에게만 50% 반환한다.
            // 상대방은 여전히 노쇼 당사자이므로 여기서 피해자 전액 반환으로 처리하지 않는다.
            if (submitterIsAuthor) {
                userPointService.partialRefundPoint(
                        post.getAuthorId(),
                        post.getAuthorDeposit(),
                        match.getId()
                );
                refundedPoint = post.getAuthorDeposit() / 2;

            } else if (submitterIsApplicant) {
                userPointService.partialRefundPoint(
                        match.getApplicantId(),
                        match.getApplicantDeposit(),
                        match.getId()
                );
                refundedPoint = match.getApplicantDeposit() / 2;
            }

        } else {

            // HOST/GUEST/BOTH_NO_SHOW 외 상태는 정상 플로우 아님
            return 0;
        }

        // GUEST_NO_SHOW / BOTH_NO_SHOW는 단건 처리 후,
        // 같은 Post에 아직 MATCHED / DISPUTED 상태 Match가 남아 있으면 Post 완료하지 않음
        boolean hasRemainingActiveMatch = matchRepository.findAllByPostId(match.getPostId())
                .stream()
                .anyMatch(this::canResolveDispute);

        if (!hasRemainingActiveMatch) {
            postLifecycleService.completePost(match.getPostId());
        }

        return refundedPoint;
    }

    @Override
    public void markAuthorNoShow(Long matchId) {

        Match match = matchInternalService.getMatchById(matchId);

        // MATCHED 또는 DISPUTED 상태만 노쇼 확정 처리 가능
        // 스케줄러 배치에서 호출되므로 예외 대신 return으로 중단 방지
        if (!canFinalizeNoShow(match)) {
            return;
        }

        match.markNoShow(MatchStatus.AUTHOR_NO_SHOW);
        postLifecycleService.completePost(match.getPostId());

        Post post = postInternalService.getPostById(match.getPostId());
        // 등록자(노쇼 당사자): 예치금 전액 몰수 (패널티)
        // 신청자(피해자): 예치금 전액 환급
        userPointService.penaltyPoint(post.getAuthorId(), post.getAuthorDeposit(), matchId);
        userPointService.refundPoint(match.getApplicantId(), match.getApplicantDeposit(), matchId);
    }

    @Override
    public void markApplicantNoShow(Long matchId) {

        Match match = matchInternalService.getMatchById(matchId);

        // MATCHED 또는 DISPUTED 상태만 노쇼 확정 처리 가능
        // 스케줄러 배치에서 호출되므로 예외 대신 return으로 중단 방지
        if (!canFinalizeNoShow(match)) {
            return;
        }

        match.markNoShow(MatchStatus.APPLICANT_NO_SHOW);
        postLifecycleService.completePost(match.getPostId());

        Post post = postInternalService.getPostById(match.getPostId());
        // 등록자(피해자): 예치금 전액 환급
        // 신청자(노쇼 당사자): 예치금 전액 몰수 (패널티)
        userPointService.refundPoint(post.getAuthorId(), post.getAuthorDeposit(), matchId);
        userPointService.penaltyPoint(match.getApplicantId(), match.getApplicantDeposit(), matchId);
    }

    @Override
    public void markBothNoShow(Long matchId) {

        Match match = matchInternalService.getMatchById(matchId);

        // MATCHED 또는 DISPUTED 상태만 노쇼 확정 처리 가능
        // 스케줄러 배치에서 호출되므로 예외 대신 return으로 중단 방지
        if (!canFinalizeNoShow(match)) {
            return;
        }

        match.markNoShow(MatchStatus.BOTH_NO_SHOW);
        postLifecycleService.completePost(match.getPostId());

        Post post = postInternalService.getPostById(match.getPostId());
        // 양측 모두 노쇼 → 양측 예치금 전부 몰수
        userPointService.penaltyPoint(post.getAuthorId(), post.getAuthorDeposit(), matchId);
        userPointService.penaltyPoint(match.getApplicantId(), match.getApplicantDeposit(), matchId);
    }

    // canFinalizeNoShow: 배치 노쇼 확정 처리 가능 여부
    // MATCHED(정상 매칭 중) 또는 DISPUTED(이의제기 기각 후 노쇼 확정 흐름) 두 케이스만 허용
    // 이미 COMPLETED/CANCELLED 등으로 종결된 건은 배치에서 재처리 방지
    private boolean canFinalizeNoShow(Match match) {
        return match.getStatus() == MatchStatus.MATCHED || match.getStatus() == MatchStatus.DISPUTED;
    }

    // canResolveDispute: 이의제기 판정 결과 처리 가능 여부
    // canFinalizeNoShow와 허용 조건은 동일하지만 호출 목적이 다름
    // → "노쇼 확정(배치)" vs "이의제기 판정 후처리(관리자)" 의도를 이름으로 구분
    private boolean canResolveDispute(Match match) {
        return match.getStatus() == MatchStatus.MATCHED || match.getStatus() == MatchStatus.DISPUTED;
    }

    private void publishPostVectorDeleteEvent(Long postId) {
        if (applicationEventPublisher == null || postId == null) {
            return;
        }

        // 삭제 이벤트는 postId만 필요합니다. Listener가 커밋 이후 pgvector 테이블에서 해당 행을 제거합니다.
        applicationEventPublisher.publishEvent(new PostVectorDeleteEvent(postId));
    }
}
