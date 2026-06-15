package com.example.team3final.domain.meet.service;

import com.example.team3final.domain.dispute.service.DisputeInternalService;
import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.service.MatchInternalService;
import com.example.team3final.domain.match.service.MatchNoShowService;
import com.example.team3final.domain.match.service.NoShowDecision;
import com.example.team3final.domain.match.service.NoShowSettlementResult;
import com.example.team3final.domain.meet.context.MeetVerificationBulkContext;
import com.example.team3final.domain.meet.context.NoShowConfirmedNotificationTarget;
import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.enums.VerificationStatus;
import com.example.team3final.domain.meet.repository.MeetVerificationRepository;
import com.example.team3final.domain.meet.service.support.MeetVerificationContextReader;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.dto.response.PostInfoDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoShowPostProcessor {

    private final MeetVerificationRepository meetVerificationRepository;
    private final MatchInternalService matchInternalService;
    private final MatchNoShowService matchNoShowService;
    private final DisputeInternalService disputeInternalService;
    private final MeetVerificationContextReader contextReader;
    private final NotificationPublisher notificationPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(Long postId, List<Long> candidateMatchIds) {
        List<MeetVerification> candidates =
                meetVerificationRepository.findAllByMatchIdInWithLock(candidateMatchIds);

        if (candidates.isEmpty()) {
            return;
        }

        List<Long> siblingMatchIds = matchInternalService.getMatchIdsByPostId(postId);
        Map<Long, MatchInfoDto> siblingMatchMap =
                matchInternalService.getMatchInfos(siblingMatchIds);

        List<Long> activeMatchIds = siblingMatchMap.values().stream()
                .filter(info -> info.status() == MatchStatus.MATCHED
                        || info.status() == MatchStatus.DISPUTED)
                .map(MatchInfoDto::matchId)
                .toList();

        Set<Long> activeDisputeMatchIds =
                disputeInternalService.getMatchIdsWithActiveDispute(activeMatchIds);

        List<NoShowDecision> decisions = candidates.stream()
                .filter(candidate -> !activeDisputeMatchIds.contains(candidate.getMatchId()))
                .map(candidate -> new NoShowDecision(candidate.getMatchId(), candidate.getStatus()))
                .toList();

        boolean authorNoShow = decisions.stream().anyMatch(decision ->
                decision.status() == VerificationStatus.HOST_NO_SHOW
                        || decision.status() == VerificationStatus.BOTH_NO_SHOW
        );

        if (authorNoShow && !activeDisputeMatchIds.isEmpty()) {
            // 등록자 귀책은 그룹 전체 정산이므로 형제 Match 이의제기가 끝날 때까지 Post 전체를 보류한다.
            log.info("[노쇼확정] 그룹 이의제기 검토 중 스킵 - postId={}, matchIds={}",
                    postId, activeDisputeMatchIds);
            return;
        }

        NoShowSettlementResult result = matchNoShowService.finalizeNoShows(postId, decisions);
        if (result.processedMatchIds().isEmpty()) {
            return;
        }

        Map<Long, MeetVerification> verificationMap = candidates.stream()
                .collect(Collectors.toMap(MeetVerification::getMatchId, Function.identity()));
        MeetVerificationBulkContext bulk =
                contextReader.loadBulkMatchContext(result.processedMatchIds());
        List<NoShowConfirmedNotificationTarget> notificationTargets = new ArrayList<>();

        for (Long matchId : result.processedMatchIds()) {
            MeetVerification verification = verificationMap.get(matchId);
            MatchInfoDto matchInfo = bulk.matchInfoMap().get(matchId);

            if (verification == null || matchInfo == null) {
                continue;
            }

            PostInfoDto postInfo = bulk.postInfoMap().get(matchInfo.postId());
            if (postInfo == null) {
                continue;
            }

            VerificationStatus status = verification.getStatus();
            if (!verification.isNoShowConfirmedSent()) {
                if (status == VerificationStatus.HOST_NO_SHOW) {
                    notificationTargets.add(
                            new NoShowConfirmedNotificationTarget(postInfo.authorId(), matchId)
                    );
                } else if (status == VerificationStatus.GUEST_NO_SHOW) {
                    notificationTargets.add(
                            new NoShowConfirmedNotificationTarget(matchInfo.applicantId(), matchId)
                    );
                } else if (status == VerificationStatus.BOTH_NO_SHOW) {
                    notificationTargets.add(
                            new NoShowConfirmedNotificationTarget(postInfo.authorId(), matchId)
                    );
                    notificationTargets.add(
                            new NoShowConfirmedNotificationTarget(matchInfo.applicantId(), matchId)
                    );
                }
                verification.markNoShowConfirmedSent();
            }
            verification.confirmNoShow();
        }

        // Kafka 알림은 DB 상태와 포인트 정산이 실제 커밋된 뒤에만 발행한다.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notificationTargets.forEach(target ->
                        notificationPublisher.sendNoShowConfirmed(target.userId(), target.matchId())
                );
            }
        });
    }
}
