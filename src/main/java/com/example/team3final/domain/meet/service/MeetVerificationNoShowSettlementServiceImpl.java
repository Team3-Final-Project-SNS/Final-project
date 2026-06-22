package com.example.team3final.domain.meet.service;

import com.example.team3final.domain.dispute.service.DisputeInternalService;
import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.service.MatchInternalService;
import com.example.team3final.domain.match.service.MatchNoShowService;
import com.example.team3final.domain.match.context.NoShowDecision;
import com.example.team3final.domain.match.context.NoShowSettlementResult;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetVerificationNoShowSettlementServiceImpl
        implements MeetVerificationNoShowSettlementService {

    private final MeetVerificationRepository meetVerificationRepository;
    private final MatchInternalService matchInternalService;
    private final MatchNoShowService matchNoShowService;
    private final DisputeInternalService disputeInternalService;
    private final MeetVerificationContextReader contextReader;
    private final NotificationPublisher notificationPublisher;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void settlePost(Long postId, List<Long> candidateMatchIds) {
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

        // QR 인증 완료 상태 또는 완료 플래그가 있는 매칭은 노쇼 확정과 포인트 정산에서 제외한다.
        List<MeetVerification> settlementCandidates = candidates.stream()
                .filter(candidate -> {
                    if (!candidate.isMeetAlreadyCompleted()) {
                        return true;
                    }

                    log.warn(
                            "[노쇼확정] 완료된 만남 스킵 - matchId={}, status={}",
                            candidate.getMatchId(),
                            candidate.getStatus()
                    );
                    return false;
                })
                .toList();

        List<NoShowDecision> decisions = settlementCandidates.stream()
                .filter(candidate -> !activeDisputeMatchIds.contains(candidate.getMatchId()))
                .map(candidate -> new NoShowDecision(candidate.getMatchId(), candidate.getStatus()))
                .toList();

        if (decisions.isEmpty()) {
            return;
        }

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

        Map<Long, MeetVerification> verificationMap = settlementCandidates.stream()
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

            // 정산 처리 중 상태가 변경된 경우에도 확정 알림과 상태 변경을 마지막으로 한 번 더 차단한다.
            if (verification.isMeetAlreadyCompleted()) {
                log.warn(
                        "[노쇼확정] 발송 직전 완료된 만남 스킵 - matchId={}, status={}",
                        matchId,
                        verification.getStatus()
                );
                continue;
            }

            PostInfoDto postInfo = bulk.postInfoMap().get(matchInfo.postId());
            if (postInfo == null) {
                continue;
            }

            VerificationStatus status = verification.getStatus();
            if (!verification.isNoShowConfirmedSent()) {
                if (status == VerificationStatus.HOST_NO_SHOW
                        || status == VerificationStatus.GUEST_NO_SHOW
                        || status == VerificationStatus.BOTH_NO_SHOW) {
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

        notificationTargets.stream()
                .distinct()
                .forEach(target -> notificationPublisher.sendNoShowConfirmed(target.userId(), target.matchId()));
    }
}
