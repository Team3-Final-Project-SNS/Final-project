package com.example.team3final.domain.match.service;

import com.example.team3final.domain.chat.service.ChatInternalService;
import com.example.team3final.domain.match.context.NoShowDecision;
import com.example.team3final.domain.match.context.NoShowDisputeSettlementResult;
import com.example.team3final.domain.match.context.NoShowSettlementResult;
import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.repository.MatchRepository;
import com.example.team3final.domain.meet.enums.VerificationStatus;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.post.event.PostVectorDeleteEvent;
import com.example.team3final.domain.post.service.PostInternalService;
import com.example.team3final.domain.review.util.ReviewRedisZSetKeys;
import com.example.team3final.domain.user.service.UserPointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// Match 도메인의 노쇼 및 이의제기 결과 반영을 담당하는 서비스
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MatchNoShowServiceImpl implements MatchNoShowService {

    private static final List<MatchStatus> ACTIVE_STATUSES =
            List.of(MatchStatus.MATCHED, MatchStatus.DISPUTED);

    private final MatchRepository matchRepository;
    private final MatchInternalService matchInternalService;
    private final PostInternalService postInternalService;
    private final UserPointService userPointService;
    private final ChatInternalService chatInternalService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void markDisputed(Long matchId) {
        Match match = matchInternalService.getMatchById(matchId);
        if (match.getStatus() == MatchStatus.MATCHED) {
            match.dispute();
        }
    }

    @Override
    public int completeSingleMatchByDispute(Long matchId, Long submitterId) {
        Match match = matchInternalService.getMatchById(matchId);
        Post post = lockAndNormalizePost(match.getPostId());

        if (!isActive(match)) {
            return 0;
        }

        boolean submitterIsAuthor = submitterId.equals(post.getAuthorId());
        boolean submitterIsApplicant = submitterId.equals(match.getApplicantId());
        boolean authorAlreadySettled =
                userPointService.hasAuthorDepositSettlement(post.getAuthorId(), post.getId());

        match.completeByDispute();
        userPointService.refundApplicantDeposit(
                match.getApplicantId(),
                match.getApplicantDeposit(),
                match.getId()
        );
        scheduleReviewReminder(match);

        // 마지막 활성 Match가 끝날 때만 Post와 등록자 책임비를 한 번 정산한다.
        boolean postCompleted = completePostIfNoActiveMatches(post);
        if (submitterIsAuthor && postCompleted && !authorAlreadySettled) {
            return post.getAuthorDeposit();
        }
        return submitterIsApplicant ? match.getApplicantDeposit() : 0;
    }

    @Override
    public NoShowDisputeSettlementResult markNoShowByDispute(
            Long matchId,
            VerificationStatus restoredStatus,
            Long submitterId
    ) {
        Match triggerMatch = matchInternalService.getMatchById(matchId);
        Post post = lockAndNormalizePost(triggerMatch.getPostId());

        if (!isActive(triggerMatch)) {
            return NoShowDisputeSettlementResult.empty(post.getId());
        }

        boolean submitterIsAuthor = submitterId.equals(post.getAuthorId());
        boolean submitterIsApplicant = submitterId.equals(triggerMatch.getApplicantId());
        int refundedPoint = 0;
        List<Long> processedIds = new ArrayList<>();

        if (restoredStatus == VerificationStatus.HOST_NO_SHOW) {
            // 일반 MATCHED 형제와 현재 판정 중인 DISPUTED Match만 처리한다.
            // 다른 사용자의 DISPUTED Match는 해당 이의제기 판정 전까지 그대로 보존한다.
            for (Match targetMatch : getAuthorNoShowTargets(post.getId(), triggerMatch)) {
                targetMatch.markNoShow(MatchStatus.HOST_NO_SHOW);
                userPointService.refundApplicantDeposit(
                        targetMatch.getApplicantId(),
                        targetMatch.getApplicantDeposit(),
                        targetMatch.getId()
                );
                processedIds.add(targetMatch.getId());
                if (submitterId.equals(targetMatch.getApplicantId())) {
                    refundedPoint = targetMatch.getApplicantDeposit();
                }
            }

            if (submitterIsAuthor) {
                settleAuthorPartialRefund(post);
                refundedPoint = post.getAuthorDeposit() / 2;
            }

            completePostIfNoActiveMatches(post);
            return new NoShowDisputeSettlementResult(post.getId(), processedIds, refundedPoint);
        }

        if (restoredStatus == VerificationStatus.GUEST_NO_SHOW) {
            triggerMatch.markNoShow(MatchStatus.GUEST_NO_SHOW);
            if (submitterIsApplicant) {
                userPointService.partialRefundApplicantDeposit(
                        triggerMatch.getApplicantId(),
                        triggerMatch.getApplicantDeposit(),
                        triggerMatch.getId()
                );
                refundedPoint = triggerMatch.getApplicantDeposit() / 2;
            } else {
                userPointService.penaltyApplicantDeposit(
                        triggerMatch.getApplicantId(),
                        triggerMatch.getApplicantDeposit(),
                        triggerMatch.getId()
                );
            }
            processedIds.add(triggerMatch.getId());
        } else if (restoredStatus == VerificationStatus.BOTH_NO_SHOW) {
            for (Match targetMatch : getAuthorNoShowTargets(post.getId(), triggerMatch)) {
                if (targetMatch.getId().equals(triggerMatch.getId())) {
                    targetMatch.markNoShow(MatchStatus.BOTH_NO_SHOW);
                    if (submitterIsApplicant) {
                        userPointService.partialRefundApplicantDeposit(
                                targetMatch.getApplicantId(),
                                targetMatch.getApplicantDeposit(),
                                targetMatch.getId()
                        );
                        refundedPoint = targetMatch.getApplicantDeposit() / 2;
                    } else {
                        userPointService.penaltyApplicantDeposit(
                                targetMatch.getApplicantId(),
                                targetMatch.getApplicantDeposit(),
                                targetMatch.getId()
                        );
                    }
                } else {
                    targetMatch.markNoShow(MatchStatus.HOST_NO_SHOW);
                    userPointService.refundApplicantDeposit(
                            targetMatch.getApplicantId(),
                            targetMatch.getApplicantDeposit(),
                            targetMatch.getId()
                    );
                }
                processedIds.add(targetMatch.getId());
            }

            if (submitterIsAuthor) {
                settleAuthorPartialRefund(post);
                refundedPoint = post.getAuthorDeposit() / 2;
            }
        } else {
            return NoShowDisputeSettlementResult.empty(post.getId());
        }

        completePostIfNoActiveMatches(post);
        return new NoShowDisputeSettlementResult(post.getId(), processedIds, refundedPoint);
    }

    @Override
    public NoShowSettlementResult markAuthorNoShow(Long matchId) {
        Match triggerMatch = matchInternalService.getMatchById(matchId);
        Post post = lockAndNormalizePost(triggerMatch.getPostId());
        List<Match> activeMatches = getAuthorNoShowTargets(post.getId(), triggerMatch);

        if (activeMatches.isEmpty()) {
            return NoShowSettlementResult.empty(post.getId());
        }

        List<Long> processedIds = new ArrayList<>();
        for (Match activeMatch : activeMatches) {
            activeMatch.markNoShow(MatchStatus.HOST_NO_SHOW);
            userPointService.refundApplicantDeposit(
                    activeMatch.getApplicantId(),
                    activeMatch.getApplicantDeposit(),
                    activeMatch.getId()
            );
            processedIds.add(activeMatch.getId());
        }

        // 다른 DISPUTED 건이 남았다면 등록자 최종 정산은 마지막 판정까지 보류한다.
        completePostIfNoActiveMatches(post);
        return new NoShowSettlementResult(post.getId(), processedIds);
    }

    @Override
    public NoShowSettlementResult markApplicantNoShow(Long matchId) {
        Match match = matchInternalService.getMatchById(matchId);
        Post post = lockAndNormalizePost(match.getPostId());

        if (!isActive(match)) {
            return NoShowSettlementResult.empty(post.getId());
        }

        match.markNoShow(MatchStatus.GUEST_NO_SHOW);
        userPointService.penaltyApplicantDeposit(
                match.getApplicantId(),
                match.getApplicantDeposit(),
                match.getId()
        );

        completePostIfNoActiveMatches(post);
        return new NoShowSettlementResult(post.getId(), List.of(match.getId()));
    }

    @Override
    public NoShowSettlementResult markBothNoShow(Long matchId) {
        Match triggerMatch = matchInternalService.getMatchById(matchId);
        Post post = lockAndNormalizePost(triggerMatch.getPostId());
        List<Match> activeMatches = getAuthorNoShowTargets(post.getId(), triggerMatch);

        if (activeMatches.isEmpty()) {
            return NoShowSettlementResult.empty(post.getId());
        }

        List<Long> processedIds = new ArrayList<>();
        for (Match activeMatch : activeMatches) {
            if (activeMatch.getId().equals(matchId)) {
                activeMatch.markNoShow(MatchStatus.BOTH_NO_SHOW);
                userPointService.penaltyApplicantDeposit(
                        activeMatch.getApplicantId(),
                        activeMatch.getApplicantDeposit(),
                        activeMatch.getId()
                );
            } else {
                // 등록자 결석은 그룹 전체에 적용되지만, 다른 신청자의 귀책까지 만들지는 않는다.
                activeMatch.markNoShow(MatchStatus.HOST_NO_SHOW);
                userPointService.refundApplicantDeposit(
                        activeMatch.getApplicantId(),
                        activeMatch.getApplicantDeposit(),
                        activeMatch.getId()
                );
            }
            processedIds.add(activeMatch.getId());
        }

        completePostIfNoActiveMatches(post);
        return new NoShowSettlementResult(post.getId(), processedIds);
    }

    @Override
    public NoShowSettlementResult finalizeNoShows(Long postId, List<NoShowDecision> decisions) {
        Post post = lockAndNormalizePost(postId);
        List<Match> activeMatches = getActiveMatches(postId);

        if (activeMatches.isEmpty()) {
            return NoShowSettlementResult.empty(postId);
        }

        Map<Long, NoShowDecision> decisionMap = decisions.stream()
                .collect(Collectors.toMap(NoShowDecision::matchId, Function.identity()));

        boolean authorNoShow = decisions.stream().anyMatch(decision ->
                decision.status() == VerificationStatus.HOST_NO_SHOW
                        || decision.status() == VerificationStatus.BOTH_NO_SHOW
        );

        if (authorNoShow && activeMatches.stream().anyMatch(match -> !decisionMap.containsKey(match.getId()))) {
            // 등록자 귀책은 그룹 전체에 영향을 주므로 모든 활성 Match의 판정이 모일 때까지 보류한다.
            return NoShowSettlementResult.empty(postId);
        }

        List<Long> processedIds = new ArrayList<>();
        for (Match match : activeMatches) {
            NoShowDecision decision = decisionMap.get(match.getId());
            if (decision == null) {
                continue;
            }

            if (authorNoShow) {
                if (decision.status() == VerificationStatus.BOTH_NO_SHOW
                        || decision.status() == VerificationStatus.GUEST_NO_SHOW) {
                    // 그룹에서 등록자 결석이 확인됐고 이 신청자도 결석이면 최종 결과는 양측 노쇼다.
                    match.markNoShow(MatchStatus.BOTH_NO_SHOW);
                    userPointService.penaltyApplicantDeposit(
                            match.getApplicantId(),
                            match.getApplicantDeposit(),
                            match.getId()
                    );
                } else {
                    match.markNoShow(MatchStatus.HOST_NO_SHOW);
                    userPointService.refundApplicantDeposit(
                            match.getApplicantId(),
                            match.getApplicantDeposit(),
                            match.getId()
                    );
                }
            } else if (decision.status() == VerificationStatus.GUEST_NO_SHOW) {
                match.markNoShow(MatchStatus.GUEST_NO_SHOW);
                userPointService.penaltyApplicantDeposit(
                        match.getApplicantId(),
                        match.getApplicantDeposit(),
                        match.getId()
                );
            } else {
                continue;
            }
            processedIds.add(match.getId());
        }

        completePostIfNoActiveMatches(post);
        return new NoShowSettlementResult(postId, processedIds);
    }

    private Post lockAndNormalizePost(Long postId) {
        Post post = postInternalService.getPostByIdWithLock(postId);

        // 신청자가 한 명이라도 있으면 정책상 성립된 모임이다.
        // 과거 스케줄러가 잘못 만든 EXPIRED + 활성 Match 데이터도 같은 정상 상태로 복구한다.
        if ((post.getStatus() == PostStatus.OPEN || post.getStatus() == PostStatus.EXPIRED)
                && post.getCurrentApplicants() >= 2) {
            post.match();
        }
        return post;
    }

    private List<Match> getActiveMatches(Long postId) {
        return matchRepository.findAllByPostIdAndStatusInOrderByIdAsc(postId, ACTIVE_STATUSES);
    }

    private List<Match> getAuthorNoShowTargets(Long postId, Match triggerMatch) {
        List<Match> targets = new ArrayList<>(
                matchRepository.findAllByPostIdAndStatusOrderByIdAsc(postId, MatchStatus.MATCHED)
        );

        // 관리자 판정 대상인 현재 DISPUTED Match만 포함하고 다른 이의제기 건은 보존한다.
        if (triggerMatch.getStatus() == MatchStatus.DISPUTED
                && targets.stream().noneMatch(match -> match.getId().equals(triggerMatch.getId()))) {
            targets.add(triggerMatch);
        }
        return targets;
    }

    private boolean completePostIfNoActiveMatches(Post post) {
        if (matchRepository.countByPostIdAndStatusIn(post.getId(), ACTIVE_STATUSES) > 0) {
            return false;
        }
        if (post.getStatus() == PostStatus.COMPLETED) {
            return false;
        }
        if (post.getStatus() != PostStatus.MATCHED) {
            throw new IllegalStateException("활성 매칭이 종료됐지만 Post가 MATCHED 상태가 아닙니다. postId=" + post.getId());
        }

        // 등록자 정산이 아직 없다면 종료된 Match 결과를 기준으로 최종 환급/패널티를 한 번 결정한다.
        if (!userPointService.hasAuthorDepositSettlement(post.getAuthorId(), post.getId())) {
            boolean authorWasNoShow = matchRepository.findAllByPostId(post.getId()).stream()
                    .anyMatch(match -> match.getStatus() == MatchStatus.HOST_NO_SHOW
                            || match.getStatus() == MatchStatus.BOTH_NO_SHOW);
            if (authorWasNoShow) {
                settleAuthorPenalty(post);
            } else {
                settleAuthorRefund(post);
            }
        }

        post.complete();
        publishPostVectorDeleteEvent(post.getId());
        if (chatInternalService.existsChatRoomByPostId(post.getId())) {
            chatInternalService.scheduleChatRoomDeactivation(post.getId());
        } else {
            log.warn("[노쇼확정] 채팅방 없음 - 채팅방 종료 예약 스킵, postId={}", post.getId());
        }
        return true;
    }

    private void settleAuthorRefund(Post post) {
        if (!userPointService.hasAuthorDepositSettlement(post.getAuthorId(), post.getId())) {
            userPointService.refundAuthorDeposit(
                    post.getAuthorId(),
                    post.getAuthorDeposit(),
                    post.getId()
            );
        }
    }

    private void settleAuthorPartialRefund(Post post) {
        if (!userPointService.hasAuthorDepositSettlement(post.getAuthorId(), post.getId())) {
            userPointService.partialRefundAuthorDeposit(
                    post.getAuthorId(),
                    post.getAuthorDeposit(),
                    post.getId()
            );
        }
    }

    private void settleAuthorPenalty(Post post) {
        if (!userPointService.hasAuthorDepositSettlement(post.getAuthorId(), post.getId())) {
            userPointService.penaltyAuthorDeposit(
                    post.getAuthorId(),
                    post.getAuthorDeposit(),
                    post.getId()
            );
        }
    }

    private boolean isActive(Match match) {
        return ACTIVE_STATUSES.contains(match.getStatus());
    }

    private void scheduleReviewReminder(Match match) {
        LocalDateTime reminderAt = match.getCompletedAt()
                .plusMinutes(7);
        // TODO 임시 테스트용 설정입니다. 추후 기존 정책인 7일 마지막 날 오전 9시로 되돌릴 예정입니다.
        // LocalDateTime reminderAt = match.getCompletedAt()
        //         .plusDays(7)
        //         .toLocalDate()
        //         .atTime(9, 0);
        redisTemplate.opsForZSet().add(
                ReviewRedisZSetKeys.DEADLINE_REMINDER,
                String.valueOf(match.getId()),
                reminderAt.toEpochSecond(ZoneOffset.ofHours(9))
        );
    }

    private void publishPostVectorDeleteEvent(Long postId) {
        applicationEventPublisher.publishEvent(new PostVectorDeleteEvent(postId));
    }
}
