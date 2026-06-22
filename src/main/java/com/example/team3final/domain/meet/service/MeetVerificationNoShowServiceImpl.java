package com.example.team3final.domain.meet.service;

import com.example.team3final.domain.chat.service.ChatInternalService;
import com.example.team3final.domain.location.service.UserLocationCleanupService;
import com.example.team3final.domain.location.service.UserLocationService;
import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.service.MatchInternalService;
import com.example.team3final.domain.meet.context.MeetVerificationBulkContext;
import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.enums.VerificationStatus;
import com.example.team3final.domain.meet.repository.MeetVerificationRepository;
import com.example.team3final.domain.meet.service.support.MeetVerificationContextReader;
import com.example.team3final.domain.meet.service.support.MeetVerificationPolicy;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.dto.response.PostInfoDto;
import com.example.team3final.domain.post.service.PostInternalService;
import com.example.team3final.domain.user.service.UserInternalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// MeetVerification 도메인의 노쇼 판정 및 노쇼 확정 처리를 담당하는 서비스
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MeetVerificationNoShowServiceImpl implements MeetVerificationNoShowService {

    private final MeetVerificationRepository meetVerificationRepository;
    private final UserLocationCleanupService userLocationCleanupService;
    private final ChatInternalService chatInternalService;
    private final PostInternalService postInternalService;
    private final MatchInternalService matchInternalService;
    private final UserLocationService userLocationService;
    private final NotificationPublisher notificationPublisher;
    private final MeetVerificationContextReader contextReader;
    private final MeetVerificationNoShowSettlementService noShowSettlementService;
    private final UserInternalService userInternalService;

    // GPS 노쇼 배치 판정 — 스케줄러가 주기적으로 호출
    // 판정 대상: PENDING 상태 (양측 GPS 인증이 모두 완료되지 않은 매칭)
    // 판정 시점: meetAt + 10분이 지난 건 (장소 인증 가능 시간이 끝난 후)
    @Override
    @Transactional
    public void judgeGpsNoShow() {

        LocalDateTime now = LocalDateTime.now();

        // PENDING 상태인 MeetVerification 전체 조회 (쿼리 1번)
        // PENDING = 양측 GPS 장소 인증이 모두 완료되지 않은 매칭
        List<MeetVerification> pendingList = meetVerificationRepository.findAllByStatus(VerificationStatus.PENDING);

        // 빈 리스트 방어 — IN쿼리 빈 컬렉션 오류 + 불필요한 외부 서비스 호출 차단
        if (pendingList.isEmpty()) {
            return;
        }

        // matchId 목록 추출
        List<Long> matchIds = pendingList.stream()
                .map(MeetVerification::getMatchId)
                .toList();

        // Match + Post 벌크 조회 (N+1 방지)
        MeetVerificationBulkContext bulk = contextReader.loadBulkMatchContext(matchIds);

        for (MeetVerification meetVerification : pendingList) {

            // QR 인증까지 완료된 만남은 상태 데이터가 PENDING으로 오염됐더라도 노쇼 판정에서 제외한다.
            if (meetVerification.isMeetAlreadyCompleted()) {
                log.warn(
                        "[GPS 노쇼판정] 완료된 만남 스킵 - matchId={}, status={}",
                        meetVerification.getMatchId(),
                        meetVerification.getStatus()
                );
                continue;
            }

            MatchInfoDto matchInfoDto = bulk.matchInfoMap().get(meetVerification.getMatchId());
            if (matchInfoDto == null) {
                // 데이터 정합성이 깨졌을 때를 대비한 방어 — 해당 건만 스킵
                continue;
            }

            // MATCHED 상태가 아닌 매칭(CANCELLED 등 종료 상태)은 노쇼 판정 대상에서 제외
            // 매칭 취소 후 MeetVerification이 PENDING으로 남아 오발송되는 버그 방어
            if (matchInfoDto.status() != MatchStatus.MATCHED) {
                continue;
            }

            PostInfoDto postInfoDto = bulk.postInfoMap().get(matchInfoDto.postId());
            if (postInfoDto == null) {
                continue;
            }

            // 연장 수락된 매칭은 원래 meetAt이 아닌 extendedMeetAt을 기준으로 노쇼 판정
            // 연장 안 했으면 extendedMeetAt이 null이므로 원래 meetAt 사용
            LocalDateTime effectiveMeetAt = meetVerification.getExtendedMeetAt() != null
                    ? meetVerification.getExtendedMeetAt()
                    : postInfoDto.meetAt();

            // 판정 기준 시각(meetAt + 10분, 장소 인증 종료 시각)이 아직 안 지났으면 이번 배치에서 스킵
            // 다음 스케줄러 실행 때 다시 평가됨
            LocalDateTime deadline = effectiveMeetAt.plusMinutes(
                    MeetVerificationPolicy.NO_SHOW_JUDGE_MINUTES);
            if (now.isBefore(deadline)) {
                continue;
            }

            // 양측 GPS 인증 여부 확인
            boolean authorVerified = meetVerification.isAuthorPlaceVerified();
            boolean applicantVerified = meetVerification.isApplicantPlaceVerified();

            // 노쇼 판정 분기
            if (!authorVerified && !applicantVerified) {
                meetVerification.markBothNoShow();
                userLocationCleanupService.deleteLocationsByMatchId(meetVerification.getMatchId());
                // BOTH_NO_SHOW → 채팅방 전체 READ_ONLY
                chatInternalService.makeReadOnlyChatRoom(matchInfoDto.postId());
                // 발송 플래그 확인 → 중복 발송 방지
                if (!meetVerification.isNoShowWarningSent()) {
                    notificationPublisher.sendNoShowWarning(postInfoDto.authorId(), meetVerification.getMatchId());
                    notificationPublisher.sendNoShowWarning(matchInfoDto.applicantId(), meetVerification.getMatchId());
                    meetVerification.markNoShowWarningSent();
                }

            } else if (authorVerified && !applicantVerified) {
                meetVerification.markApplicantNoShow();
                userLocationCleanupService.deleteLocationsByMatchId(meetVerification.getMatchId());
                // GUEST_NO_SHOW → 해당 신청자만 NO_SHOW 처리
                chatInternalService.markGuestNoShow(matchInfoDto.postId(), matchInfoDto.applicantId());
                // 발송 플래그 확인 → 중복 발송 방지
                if (!meetVerification.isNoShowWarningSent()) {
                    sendApplicantNoShowWarnings(postInfoDto, matchInfoDto, meetVerification.getMatchId());
                    meetVerification.markNoShowWarningSent();
                }

            } else if (!authorVerified) {
                meetVerification.markAuthorNoShow();
                userLocationCleanupService.deleteLocationsByMatchId(meetVerification.getMatchId());
                // HOST_NO_SHOW → 채팅방 전체 READ_ONLY
                chatInternalService.makeReadOnlyChatRoom(matchInfoDto.postId());
                // 발송 플래그 확인 → 중복 발송 방지
                if (!meetVerification.isNoShowWarningSent()) {
                    sendAuthorNoShowWarnings(postInfoDto, matchInfoDto, meetVerification.getMatchId());
                    meetVerification.markNoShowWarningSent();
                }
            }
        }
    }

    // QR 노쇼 배치 판정 — 스케줄러가 주기적으로 호출
    // 판정 대상: VERIFIED 상태 + QR 만료 시간이 지난 매칭
    //           (장소는 도착했는데 QR 발급 후 10분 안에 QR 인증을 못 한 케이스)
    @Override
    @Transactional
    public void judgeQrNoShow() {

        // VERIFIED 상태 + QR 만료 시간이 지난 MeetVerification 전체 조회
        // VERIFIED = 양측 장소 인증은 끝났지만 QR 스캔이 아직 완료되지 않은 상태
        List<MeetVerification> expiresList = meetVerificationRepository
                .findAllByStatusAndQrExpiresAtBefore(
                        VerificationStatus.VERIFIED,
                        LocalDateTime.now()
                );

        // 처리할 QR 만료 대상이 없으면 바로 종료
        if (expiresList.isEmpty()) {
            return;
        }

        // QR 만료 대상의 matchId 목록 추출
        List<Long> matchIds = expiresList.stream()
                .map(MeetVerification::getMatchId)
                .toList();

        // Match 정보를 한 번에 조회해서 N+1 방지
        Map<Long, MatchInfoDto> matchInfoDtoMap = matchInternalService.getMatchInfos(matchIds);

        for (MeetVerification meetVerification : expiresList) {

            Long matchId = meetVerification.getMatchId();

            // QR 인증 완료 플래그가 true인 만남은 상태가 VERIFIED로 남아 있어도 노쇼 판정하지 않는다.
            if (meetVerification.isMeetAlreadyCompleted()) {
                log.warn(
                        "[QR 노쇼판정] 완료된 만남 스킵 - matchId={}, status={}",
                        matchId,
                        meetVerification.getStatus()
                );
                continue;
            }

            // 현재 MeetVerification에 대응되는 Match 정보 조회
            MatchInfoDto matchInfoDto = matchInfoDtoMap.get(matchId);

            // 데이터 정합성 방어
            // Match 정보가 없으면 해당 건은 스킵
            if (matchInfoDto == null) {
                continue;
            }

            // Post 정보 조회
            // 장소 좌표, 등록자 ID를 얻기 위해 필요
            // MATCHED 상태가 아닌 매칭(CANCELLED 등 종료 상태)은 QR 노쇼 판정 대상에서 제외
            // 취소된 MeetVerification이 VERIFIED로 남아 오발송되는 버그 방어
            if (matchInfoDto.status() != MatchStatus.MATCHED) {
                continue;
            }

            PostInfoDto postInfoDto = postInternalService.getPostInfo(matchInfoDto.postId());

            // 데이터 정합성 방어
            // Post 정보가 없으면 해당 건은 스킵
            if (postInfoDto == null) {
                continue;
            }

            // 등록자가 QR 만료 시점에 아직 약속 장소 반경 안에 있는지 확인
            boolean authorInRange = userLocationService.isFreshLocationWithinRadius(
                    matchId,
                    postInfoDto.authorId(),
                    postInfoDto.placeLat(),
                    postInfoDto.placeLng(),
                    MeetVerificationPolicy.NO_SHOW_RADIUS_METERS,
                    MeetVerificationPolicy.LOCATION_FRESHNESS_SECONDS
            );

            // 신청자가 QR 만료 시점에 아직 약속 장소 반경 안에 있는지 확인
            boolean applicantInRange = userLocationService.isFreshLocationWithinRadius(
                    matchId,
                    matchInfoDto.applicantId(),
                    postInfoDto.placeLat(),
                    postInfoDto.placeLng(),
                    MeetVerificationPolicy.NO_SHOW_RADIUS_METERS,
                    MeetVerificationPolicy.LOCATION_FRESHNESS_SECONDS
            );

            // 등록자가 현재 반경 안에 있는 경우
            if (authorInRange) {

                if (applicantInRange) {

                    // 양측이 장소 인증만 하고 QR 인증을 하지 않은 경우 BOTH_NO_SHOW 처리
                    meetVerification.markBothNoShow();
                    chatInternalService.makeReadOnlyChatRoom(matchInfoDto.postId());

                    // 노쇼 예정 알림 중복 발송 방지
                    if (!meetVerification.isNoShowWarningSent()) {
                        notificationPublisher.sendNoShowWarning(postInfoDto.authorId(), matchId);
                        notificationPublisher.sendNoShowWarning(matchInfoDto.applicantId(), matchId);
                        meetVerification.markNoShowWarningSent();
                    }

                } else {

                    // 등록자는 반경 안에 있고 신청자는 반경 밖에 있음
                    // → 신청자 노쇼 예정
                    meetVerification.markApplicantNoShow();

                    // GUEST 노쇼는 그룹 채팅방 전체를 잠그지 않고
                    // 해당 신청자만 채팅 제한 처리
                    chatInternalService.markGuestNoShow(
                            matchInfoDto.postId(),
                            matchInfoDto.applicantId()
                    );

                    // 노쇼 예정 알림 중복 발송 방지
                    if (!meetVerification.isNoShowWarningSent()) {
                        sendApplicantNoShowWarnings(postInfoDto, matchInfoDto, matchId);
                        meetVerification.markNoShowWarningSent();
                    }
                }

                // 등록자가 현재 반경 밖에 있는 경우
            } else {

                if (applicantInRange) {

                    // 신청자는 반경 안에 있고 등록자는 반경 밖에 있음
                    // → 등록자 노쇼 예정
                    meetVerification.markAuthorNoShow();

                    // HOST 노쇼는 모임 전체 이슈이므로 채팅방 전체 READ_ONLY 처리
                    chatInternalService.makeReadOnlyChatRoom(matchInfoDto.postId());

                    // 노쇼 예정 알림 중복 발송 방지
                    if (!meetVerification.isNoShowWarningSent()) {
                        sendAuthorNoShowWarnings(postInfoDto, matchInfoDto, matchId);
                        meetVerification.markNoShowWarningSent();
                    }

                } else {

                    // 등록자와 신청자 둘 다 현재 반경 밖에 있음
                    // 정책
                    // -> 둘 다 없다고 바로 BOTH_NO_SHOW 처리하지 않고,
                    // -> 둘 중 먼저 반경을 벗어난 사람만 노쇼 예정 처리
                    userLocationService.findFirstLeftUserId(
                            matchId,
                            postInfoDto.authorId(),
                            matchInfoDto.applicantId()
                    ).ifPresentOrElse(firstLeftUserId -> {

                        if (firstLeftUserId.equals(postInfoDto.authorId())) {

                            // 등록자가 먼저 벗어났으므로 등록자 노쇼 예정
                            meetVerification.markAuthorNoShow();

                            // HOST 노쇼는 채팅방 전체 READ_ONLY
                            chatInternalService.makeReadOnlyChatRoom(matchInfoDto.postId());

                            // 노쇼 예정 알림 중복 발송 방지
                            if (!meetVerification.isNoShowWarningSent()) {
                                sendAuthorNoShowWarnings(postInfoDto, matchInfoDto, matchId);
                                meetVerification.markNoShowWarningSent();
                            }

                        } else if (firstLeftUserId.equals(matchInfoDto.applicantId())) {

                            // 신청자가 먼저 벗어났으므로 신청자 노쇼 예정
                            meetVerification.markApplicantNoShow();

                            // GUEST 노쇼는 해당 신청자만 채팅 제한
                            chatInternalService.markGuestNoShow(
                                    matchInfoDto.postId(),
                                    matchInfoDto.applicantId()
                            );

                            // 노쇼 예정 알림 중복 발송 방지
                            if (!meetVerification.isNoShowWarningSent()) {
                                sendApplicantNoShowWarnings(postInfoDto, matchInfoDto, matchId);
                                meetVerification.markNoShowWarningSent();
                            }
                        }

                    }, () -> {

                        // 데이터 부족으로 먼저 벗어난 사람을 판단할 수 없는 예외 상황
                        // 정책상 정상 데이터라면 여기로 오면 안 됨
                        // 다만 앱 종료, 위치 업데이트 중단, 이탈 시각 누락 등이 있을 수 있으므로
                        // 안전하게 BOTH_NO_SHOW로 처리
                        meetVerification.markBothNoShow();

                        // BOTH_NO_SHOW는 HOST 노쇼가 포함되므로 채팅방 전체 READ_ONLY
                        chatInternalService.makeReadOnlyChatRoom(matchInfoDto.postId());

                        // 노쇼 예정 알림 중복 발송 방지
                        if (!meetVerification.isNoShowWarningSent()) {
                            notificationPublisher.sendNoShowWarning(
                                    postInfoDto.authorId(),
                                    matchId
                            );
                            notificationPublisher.sendNoShowWarning(
                                    matchInfoDto.applicantId(),
                                    matchId
                            );
                            meetVerification.markNoShowWarningSent();
                        }
                    });
                }
            }

            // QR 노쇼/취소 판정이 끝난 Match의 위치 정보는 삭제
            userLocationCleanupService.deleteLocationsByMatchId(matchId);
        }
    }

    private void sendApplicantNoShowWarnings(
            PostInfoDto postInfoDto,
            MatchInfoDto matchInfoDto,
            Long matchId
    ) {
        notificationPublisher.sendNoShowWarning(matchInfoDto.applicantId(), matchId);

        String applicantNickname = userInternalService
                .getUserInfo(matchInfoDto.applicantId())
                .nickname();
        notificationPublisher.sendOpponentNoShowWarning(
                postInfoDto.authorId(),
                matchId,
                applicantNickname
        );
    }

    private void sendAuthorNoShowWarnings(
            PostInfoDto postInfoDto,
            MatchInfoDto matchInfoDto,
            Long matchId
    ) {
        notificationPublisher.sendNoShowWarning(postInfoDto.authorId(), matchId);

        String authorNickname = userInternalService
                .getUserInfo(postInfoDto.authorId())
                .nickname();
        notificationPublisher.sendOpponentNoShowWarning(
                matchInfoDto.applicantId(),
                matchId,
                authorNickname
        );
    }

    // 노쇼 확정 — 스케줄러가 주기적으로 호출
    // 노쇼 예정 상태에서 24시간이 지나면 최종 확정 처리
    @Override
    @Transactional(readOnly = true)
    public void judgeNoShowConfirmed() {

        LocalDateTime deadline = LocalDateTime.now().minusHours(
                MeetVerificationPolicy.NO_SHOW_CONFIRM_HOURS);

        List<MeetVerification> noShowList =
                meetVerificationRepository
                        .findAllByStatusInAndNoShowDecidedAtBefore(
                                MeetVerificationPolicy.NO_SHOW_STATUSES,
                                deadline
                        );

        if (noShowList.isEmpty()) {
            return;
        }

        List<Long> matchIds = noShowList.stream()
                .map(MeetVerification::getMatchId)
                .toList();

        log.info("[노쇼확정] 배치 시작 - deadline={}, matchIds={}",
                deadline, matchIds);

        MeetVerificationBulkContext bulk = contextReader.loadBulkMatchContext(matchIds);
        Map<Long, List<Long>> matchIdsByPostId = noShowList.stream()
                .map(MeetVerification::getMatchId)
                .filter(bulk.matchInfoMap()::containsKey)
                .collect(Collectors.groupingBy(
                        matchId -> bulk.matchInfoMap().get(matchId).postId()
                ));

        for (Map.Entry<Long, List<Long>> entry : matchIdsByPostId.entrySet()) {
            try {
                // Post별 REQUIRES_NEW 처리로 한 그룹 실패가 다른 그룹 알림까지 롤백시키지 않게 한다.
                noShowSettlementService.settlePost(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                log.error(
                        "[노쇼확정] Post 처리 실패 - postId={}, matchIds={}, exception={}, message={}",
                        entry.getKey(),
                        entry.getValue(),
                        e.getClass().getSimpleName(),
                        e.getMessage(),
                        e
                );
            }
        }
    }

    // Match 정산 서비스가 실제 처리한 대상만 확정해 다른 사용자의 DISPUTE를 보존한다.
    @Override
    @Transactional
    public void confirmNoShows(List<Long> matchIds) {
        if (matchIds == null || matchIds.isEmpty()) {
            return;
        }

        List<MeetVerification> siblingMvList =
                meetVerificationRepository.findAllByMatchIdIn(matchIds);

        for (MeetVerification mv : siblingMvList) {

            // 관리자 판정으로 노쇼 확정 가능한 상태만 변경
            // 다른 사용자의 DISPUTE는 별도 관리자 판정 대상이므로 함께 확정하지 않는다.
            if (mv.getStatus() == VerificationStatus.HOST_NO_SHOW
                    || mv.getStatus() == VerificationStatus.GUEST_NO_SHOW
                    || mv.getStatus() == VerificationStatus.BOTH_NO_SHOW
                    || mv.getStatus() == VerificationStatus.DISPUTE) {
                mv.confirmNoShowByAdmin();
            }
        }
    }

    // 노쇼 후보군 조회 (Admin 전용)
    @Transactional(readOnly = true)
    @Override
    public Page<MeetVerification> getNoShowCandidates(Pageable pageable) {
        return meetVerificationRepository.findAllByStatusIn(
                MeetVerificationPolicy.NO_SHOW_STATUSES, pageable);
    }
}
