package com.example.team3final.domain.meet.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.MeetException;
import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.service.MatchInternalService;
import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.enums.ExtensionStatus;
import com.example.team3final.domain.meet.enums.VerificationStatus;
import com.example.team3final.domain.meet.repository.MeetVerificationRepository;
import com.example.team3final.domain.meet.service.support.MeetExtensionSupport;
import com.example.team3final.domain.meet.service.support.MeetVerificationPolicy;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// MeetVerification 도메인의 타 도메인/스케줄러 호출용 내부 기능을 제공하는 서비스
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MeetVerificationInternalServiceImpl implements MeetVerificationInternalService {

    private final MeetVerificationRepository meetVerificationRepository;
    private final MatchInternalService matchInternalService;
    private final NotificationPublisher notificationPublisher;
    private final MeetExtensionSupport meetExtensionSupport;

    // 매칭 생성 시 PENDING 초기화
    @Override
    public void createPendingVerification(Long matchId) {
        // 매칭 생성 시점에 PENDING 상태로 MeetVerification 레코드 초기화
        meetVerificationRepository.save(MeetVerification.createPending(matchId));
    }

    // 연장 요청 타임아웃 일괄 처리 — 스케줄러가 주기적으로 호출
    @Override
    public void expireTimeoutExtensions() {

        // REQUESTED 상태이면서 요청 시각이 5분보다 오래된 MeetVerification을 조회
        // 즉, 등록자가 제한 시간 안에 수락/거절하지 않은 연장 요청
        LocalDateTime expireThreshold = LocalDateTime.now().minusMinutes(MeetVerificationPolicy.EXTENSION_TIMEOUT_MINUTES);

        // 만료 대상 MeetVerification 목록 조회
        List<MeetVerification> expiredList = meetVerificationRepository
                .findAllByExtensionStatusAndExtensionRequestedAtBefore(ExtensionStatus.REQUESTED, expireThreshold);

        // 만료 대상이 없으면 바로 종료
        if (expiredList.isEmpty()) {
            return;
        }

        // 같은 Post가 여러 MV로 중복 조회될 수 있으므로, 이미 처리한 postId를 저장
        Set<Long> processedPostIds = new java.util.HashSet<>();

        // 만료된 MeetVerification들을 순회
        for (MeetVerification expiredMv : expiredList) {

            // MeetVerification에는 postId가 없으므로 matchId를 통해 MatchInfo를 조회
            MatchInfoDto matchInfo = matchInternalService.getMatchInfo(expiredMv.getMatchId());

            // 같은 Post를 이미 처리했다면 중복 만료 처리를 하지 않음
            if (!processedPostIds.add(matchInfo.postId())) {
                continue;
            }

            // 같은 Post에 속한 모든 Match ID를 조회
            List<Long> siblingMatchIds = matchInternalService.getMatchIdsByPostId(matchInfo.postId());

            // 비어 있으면 스킵
            if (siblingMatchIds.isEmpty()) {
                continue;
            }

            // 같은 Post에 속한 모든 MeetVerification을 락으로 조회
            // 스케줄러 만료 처리와 등록자의 수락/거절 요청이 겹칠 때 상태 충돌을 방지
            List<MeetVerification> siblingMvList =
                    meetVerificationRepository.findAllByMatchIdInWithLock(siblingMatchIds);

            // 만료 알림을 보낼 요청자 ID를 확보
            Long requesterId = expiredMv.getExtensionRequesterId();

            // 같은 Post의 REQUESTED 상태 MeetVerification을 모두 EXPIRED 처리
            for (MeetVerification mv : siblingMvList) {

                // REQUESTED 상태인 항목만 만료 처리
                if (mv.getExtensionStatus() == ExtensionStatus.REQUESTED) {
                    mv.expireExtension();
                }

                // 만료 처리 후 각 MV의 타임아웃 예약을 제거
                meetExtensionSupport.removeExtensionTimeout(mv);
            }

            // requesterId가 존재할 때만 만료 알림을 보냄
            // 데이터가 오염되어 requesterId가 null이면 NPE 방지를 위해 알림을 생략
            if (requesterId != null) {
                notificationPublisher.sendMeetExtendExpired(
                        requesterId,
                        expiredMv.getMatchId()
                );
            }
        }
    }

    // matchId로 MeetVerification 단건 조회 (외부 도메인 사용)
    @Override
    @Transactional(readOnly = true)
    public MeetVerification getByMatchId(Long matchId) {
        return meetVerificationRepository.findByMatchId(matchId)
                .orElseThrow(() -> new MeetException(ErrorCode.MEET_VERIFICATION_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> getGuestNoShowMatchIds(List<Long> matchIds) {
        if (matchIds == null || matchIds.isEmpty()) {
            return Set.of();
        }

        return meetVerificationRepository.findAllByMatchIdIn(matchIds).stream()
                .filter(mv -> mv.getStatus() == VerificationStatus.GUEST_NO_SHOW)
                .map(MeetVerification::getMatchId)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LocalDateTime> findEffectiveExtendedMeetAtByMatchId(Long matchId) {
        return meetVerificationRepository.findEffectiveExtendedMeetAtByMatchId(matchId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, LocalDateTime> findExtendedMeetAtMapByMatchIds(List<Long> matchIds) {
        if (matchIds == null || matchIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return meetVerificationRepository.findExtendedMeetAtRowsByMatchIds(matchIds).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (LocalDateTime) row[1],
                        (first, second) -> second
                ));
    }
}
