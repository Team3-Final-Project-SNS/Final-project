package com.example.team3final.domain.match.service;

import com.example.team3final.domain.chat.service.ChatInternalService;
import com.example.team3final.domain.match.context.NoShowDecision;
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
                userPointService.hasSettlement(post.getAuthorId(), post.getId());

        match.completeByDispute();
        userPointService.refundPoint(
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
    public int markNoShowByDispute(
            Long matchId,
            VerificationStatus restoredStatus,
            Long submitterId
    ) {
        Match triggerMatch = matchInternalService.getMatchById(matchId);
        Post post = lockAndNormalizePost(triggerMatch.getPostId());

        if (!isActive(triggerMatch)) {
            return 0;
        }

        boolean submitterIsAuthor = submitterId.equals(post.getAuthorId());
        boolean submitterIsApplicant = submitterId.equals(triggerMatch.getApplicantId());
        boolean authorAlreadySettled =
                userPointService.hasSettlement(post.getAuthorId(), post.getId());
        int refundedPoint = 0;

        if (restoredStatus == VerificationStatus.HOST_NO_SHOW) {
            List<Match> activeMatches = getActiveMatches(post.getId());

            // 등록자 노쇼는 Post 전체 책임이므로 모든 활성 Match를 한 번에 종결한다.
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

            if (submitterIsAuthor) {
                settleAuthorPartialRefund(post);
                refundedPoint = post.getAuthorDeposit() / 2;
            } else {
                settleAuthorPenalty(post);
            }

            completePostIfNoActiveMatches(post);
            return refundedPoint;
        }

        if (restoredStatus == VerificationStatus.GUEST_NO_SHOW) {
            triggerMatch.markNoShow(MatchStatus.APPLICANT_NO_SHOW);
            if (submitterIsApplicant) {
                userPointService.partialRefundPoint(
                        triggerMatch.getApplicantId(),
                        triggerMatch.getApplicantDeposit(),
                        triggerMatch.getId()
                );
                refundedPoint = triggerMatch.getApplicantDeposit() / 2;
            } else {
                userPointService.penaltyPoint(
                        triggerMatch.getApplicantId(),
                        triggerMatch.getApplicantDeposit(),
                        triggerMatch.getId()
                );
            }
        } else if (restoredStatus == VerificationStatus.BOTH_NO_SHOW) {
            triggerMatch.markNoShow(MatchStatus.BOTH_NO_SHOW);

            if (submitterIsApplicant) {
                userPointService.partialRefundPoint(
                        triggerMatch.getApplicantId(),
                        triggerMatch.getApplicantDeposit(),
                        triggerMatch.getId()
                );
                refundedPoint = triggerMatch.getApplicantDeposit() / 2;
            } else {
                userPointService.penaltyPoint(
                        triggerMatch.getApplicantId(),
                        triggerMatch.getApplicantDeposit(),
                        triggerMatch.getId()
                );
            }

            if (submitterIsAuthor) {
                settleAuthorPartialRefund(post);
                refundedPoint = post.getAuthorDeposit() / 2;
            }
        } else {
            return 0;
        }

        boolean postCompleted = completePostIfNoActiveMatches(post);
        if (postCompleted && submitterIsAuthor && refundedPoint == 0 && !authorAlreadySettled) {
            refundedPoint = post.getAuthorDeposit();
        }
        return refundedPoint;
    }

    @Override
    public NoShowSettlementResult markAuthorNoShow(Long matchId) {
        Match triggerMatch = matchInternalService.getMatchById(matchId);
        Post post = lockAndNormalizePost(triggerMatch.getPostId());
        List<Match> activeMatches = getActiveMatches(post.getId());

        if (activeMatches.isEmpty()) {
            return NoShowSettlementResult.empty(post.getId());
        }

        List<Long> processedIds = new ArrayList<>();
        for (Match activeMatch : activeMatches) {
            activeMatch.markNoShow(MatchStatus.AUTHOR_NO_SHOW);
            userPointService.refundPoint(
                    activeMatch.getApplicantId(),
                    activeMatch.getApplicantDeposit(),
                    activeMatch.getId()
            );
            processedIds.add(activeMatch.getId());
        }

        // 등록자 책임비는 Match 수와 무관하게 Post당 한 번만 패널티 처리한다.
        settleAuthorPenalty(post);
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

        match.markNoShow(MatchStatus.APPLICANT_NO_SHOW);
        userPointService.penaltyPoint(
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
        List<Match> activeMatches = getActiveMatches(post.getId());

        if (activeMatches.isEmpty()) {
            return NoShowSettlementResult.empty(post.getId());
        }

        List<Long> processedIds = new ArrayList<>();
        for (Match activeMatch : activeMatches) {
            if (activeMatch.getId().equals(matchId)) {
                activeMatch.markNoShow(MatchStatus.BOTH_NO_SHOW);
                userPointService.penaltyPoint(
                        activeMatch.getApplicantId(),
                        activeMatch.getApplicantDeposit(),
                        activeMatch.getId()
                );
            } else {
                // 등록자 결석은 그룹 전체에 적용되지만, 다른 신청자의 귀책까지 만들지는 않는다.
                activeMatch.markNoShow(MatchStatus.AUTHOR_NO_SHOW);
                userPointService.refundPoint(
                        activeMatch.getApplicantId(),
                        activeMatch.getApplicantDeposit(),
                        activeMatch.getId()
                );
            }
            processedIds.add(activeMatch.getId());
        }

        settleAuthorPenalty(post);
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
                    userPointService.penaltyPoint(
                            match.getApplicantId(),
                            match.getApplicantDeposit(),
                            match.getId()
                    );
                } else {
                    match.markNoShow(MatchStatus.AUTHOR_NO_SHOW);
                    userPointService.refundPoint(
                            match.getApplicantId(),
                            match.getApplicantDeposit(),
                            match.getId()
                    );
                }
            } else if (decision.status() == VerificationStatus.GUEST_NO_SHOW) {
                match.markNoShow(MatchStatus.APPLICANT_NO_SHOW);
                userPointService.penaltyPoint(
                        match.getApplicantId(),
                        match.getApplicantDeposit(),
                        match.getId()
                );
            } else {
                continue;
            }
            processedIds.add(match.getId());
        }

        if (authorNoShow) {
            settleAuthorPenalty(post);
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
        if (!userPointService.hasSettlement(post.getAuthorId(), post.getId())) {
            boolean authorWasNoShow = matchRepository.findAllByPostId(post.getId()).stream()
                    .anyMatch(match -> match.getStatus() == MatchStatus.AUTHOR_NO_SHOW
                            || match.getStatus() == MatchStatus.BOTH_NO_SHOW);
            if (authorWasNoShow) {
                settleAuthorPenalty(post);
            } else {
                settleAuthorRefund(post);
            }
        }

        post.complete();
        publishPostVectorDeleteEvent(post.getId());
        chatInternalService.scheduleChatRoomDeactivation(post.getId());
        return true;
    }

    private void settleAuthorRefund(Post post) {
        if (!userPointService.hasSettlement(post.getAuthorId(), post.getId())) {
            userPointService.refundPoint(
                    post.getAuthorId(),
                    post.getAuthorDeposit(),
                    post.getId()
            );
        }
    }

    private void settleAuthorPartialRefund(Post post) {
        if (!userPointService.hasSettlement(post.getAuthorId(), post.getId())) {
            userPointService.partialRefundPoint(
                    post.getAuthorId(),
                    post.getAuthorDeposit(),
                    post.getId()
            );
        }
    }

    private void settleAuthorPenalty(Post post) {
        if (!userPointService.hasSettlement(post.getAuthorId(), post.getId())) {
            userPointService.penaltyPoint(
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
                .plusDays(7)
                .toLocalDate()
                .atTime(9, 0);
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
