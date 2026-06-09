package com.example.team3final.domain.meet.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.MeetException;
import com.example.team3final.common.utils.GpsUtils;
import com.example.team3final.domain.chat.service.ChatService;
import com.example.team3final.domain.dispute.service.DisputeQueryService;
import com.example.team3final.domain.location.service.UserLocationCleanupService;
import com.example.team3final.domain.location.service.UserLocationService;
import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.service.MatchService;
import com.example.team3final.domain.meet.dto.request.PlaceVerificationRequestDto;
import com.example.team3final.domain.meet.dto.request.QrScanRequestDto;
import com.example.team3final.domain.meet.dto.response.*;
import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.enums.ExtensionStatus;
import com.example.team3final.domain.meet.enums.VerificationStatus;
import com.example.team3final.domain.meet.repository.MeetVerificationRepository;
import com.example.team3final.domain.meet.util.MeetRedisZSetKeys;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.dto.response.PostInfoDto;
import com.example.team3final.domain.post.service.PostService;
import com.example.team3final.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeetVerificationServiceImpl implements MeetVerificationService {

    // GPS검증, 상태 전환, 역할 구분 서비스
    private final MeetVerificationRepository meetVerificationRepository;
    private final MatchService matchService;
    private final PostService postQueryService;
    private final ChatService chatService;
    private final UserLocationService userLocationService;
    private final UserLocationCleanupService userLocationCleanupService;
    private final UserService userService;
    private final NotificationPublisher notificationPublisher;
    private final DisputeQueryService disputeQueryService;
    private final StringRedisTemplate redisTemplate; // ZSet 예약용

    // 한국 시간대 오프셋 — Unix Timestamp 변환 시 KST(UTC+9) 기준 적용
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    // QR 토큰 TTL - 상호 장소 인증 완료 시점 + 30분
    private static final long QR_TOKEN_VALIDITY_MINUTES = 30;
    // 장소 인증 가능 시간 : 만남 시간 10분 전 ~ 30분간
    private static final long VERIFICATION_BEFORE_MINUTES = 10;
    // 장소 인증 활성 시간 (만남 약속 시각 기준 20분)
    private static final long VERIFICATION_AFTER_MINUTES = 20;
    // 노쇼 판정 기준 : 장소 인증 종료 시각 기준 (meetAt - 10분 시작 + 30분 = meetAt + 20분)
    private static final long NO_SHOW_JUDGE_MINUTES = 20;
    // 연장 요청 타임아웃 : 요청 시각 + 5분
    private static final long EXTENSION_TIMEOUT_MINUTES = 5;
    // 연장 시간 : 1회 10분
    private static final long EXTENSION_MINUTES = 10;
    // 노쇼 확정까지 이의제기 가능 시간: 24시간
    private static final long NO_SHOW_CONFIRM_HOURS = 24;
    // 5초마다 위치 업데이트 정책을 기준으로 안전 여유 값 15초
    private static final long LOCATION_FRESHNESS_SECONDS = 15;
    // GPS 오차범위 고려한 노쇼 범위 (정책 50m + 오차 10m)
    private static final double NO_SHOW_RADIUS_METERS = 60.0;
    // 장소 인증 허용 반경
    // 사용자에게 안내되는 약속 장소 반경은 50m.
    // 다만 GPS 오차를 고려해서 서버 검증은 10m 여유를 둔 60m까지 허용.
    private static final double PLACE_VERIFICATION_RADIUS_METERS = 60.0;

    // 노쇼 예정 상태 목록 — 이의제기/확정 처리 대상
    private static final List<VerificationStatus> NO_SHOW_STATUSES = List.of(
            VerificationStatus.HOST_NO_SHOW,
            VerificationStatus.GUEST_NO_SHOW,
            VerificationStatus.BOTH_NO_SHOW
    );

    // GPS 장소 인증
    @Override
    @Transactional
    public PlaceVerificationResponseDto createPlaceVerification(
            Long userId,
            Long matchId,
            PlaceVerificationRequestDto requestDto) {

        // MeetVerification + MatchInfo + PostInfo 한 번에 조회
        MeetContext ctx = loadMeetContext(matchId);
        MeetVerification meetVerification = ctx.meetVerification();
        MatchInfoDto matchInfo = ctx.matchInfo();
        PostInfoDto postInfo = ctx.postInfo();

        // 매칭 당사자 검증 (등록자 or 신청자인지)
        validateParticipant(userId, matchInfo, postInfo);

        // 노쇼 예정, 이의제기 중, 노쇼 확정 상태에서는 GPS 재인증 차단
        if (NO_SHOW_STATUSES.contains(meetVerification.getStatus())
                || meetVerification.getStatus() == VerificationStatus.DISPUTE
                || meetVerification.getStatus() == VerificationStatus.NO_SHOW_CONFIRMED) {
            throw new MeetException(ErrorCode.GPS_NOT_VERIFICATION_TIME);
        }

        LocalDateTime now = LocalDateTime.now();

        // 시작 시간은 원래 약속시간 기준 10분 전으로 고정
        LocalDateTime verificationStartTime = postInfo.meetAt().minusMinutes(VERIFICATION_BEFORE_MINUTES);

        // 연장 수락 시 실제 약속시간이 바뀌므로 종료 시각도 그에 맞게 조정
        // 연장 미사용 → 원래 meetAt + 20분
        // 연장 수락 → extendedMeetAt(meetAt + 10분) + 20분
        // extendedMeetAt은 acceptExtension() 시점에 채워지므로 null이면 미사용을 의미
        LocalDateTime effectiveMeetAt = meetVerification.getExtendedMeetAt() != null
                ? meetVerification.getExtendedMeetAt()
                : postInfo.meetAt();

        LocalDateTime verificationEndTime = effectiveMeetAt.plusMinutes(VERIFICATION_AFTER_MINUTES);

        // 인증 가능 시간 범위 체크
        if (now.isBefore(verificationStartTime) || now.isAfter(verificationEndTime)) {
            throw new MeetException(ErrorCode.GPS_NOT_VERIFICATION_TIME);
        }

        // 이미 본인이 인증 완료했는지 체크
        // userId 기반으로 등록자/신청자 구분하여 각각 체크
        boolean isAuthor = userId.equals(postInfo.authorId());
        if (isAuthor && meetVerification.isAuthorPlaceVerified()) {
            throw new MeetException(ErrorCode.GPS_ALREADY_VERIFIED);
        }
        if (!isAuthor && meetVerification.isApplicantPlaceVerified()) {
            throw new MeetException(ErrorCode.GPS_ALREADY_VERIFIED);
        }

        // placeLat, placeLng를 Post에서 조회
        BigDecimal placeLat = postInfo.placeLat();
        BigDecimal placeLng = postInfo.placeLng();

        // BigDecimal → double 변환: Math 삼각함수가 double만 지원하므로 계산 직전에만 변환
        double distanceMeters = GpsUtils.calculateDistance(
                requestDto.getCurrentLat().doubleValue(), requestDto.getCurrentLng().doubleValue(),
                placeLat.doubleValue(), placeLng.doubleValue()
        );

        // 현재 위치와 약속 장소 사이의 거리가 허용 반경을 넘었는지 확인,
        // 실제 서버 검증 기준은 오차10m를 포함한 60m
        if (distanceMeters > PLACE_VERIFICATION_RADIUS_METERS) {
            // 60m 밖이면 장소 인증을 처리하지 않고 예외 발생.
            throw new MeetException(ErrorCode.GPS_OUT_OF_RANGE);
        }

        // 등록자 / 신청자 구분해서 인증 처리
        if (isAuthor) {
            // [변경] 등록자는 그룹 전체의 호스트
            // 같은 postId에 속한 모든 MeetVerification에 authorPlaceVerifiedAt 전파
            // 이유: 전파하지 않으면 다른 신청자 MV에 authorPlaceVerifiedAt = null 이 남아
            //       스케줄러가 HOST_NO_SHOW로 오판함
            propagateAuthorVerification(matchInfo.postId());
        } else {
            // 신청자는 자기 matchId의 MeetVerification에만 기록 (기존과 동일)
            meetVerification.verifyApplicantPlace();
        }

        // VERIFIED 여부 확인
        // 전파 후 이 MeetVerification의 상태가 바뀌었을 수 있으므로 엔티티에서 다시 읽음
        boolean bothVerified = meetVerification.getStatus() == VerificationStatus.VERIFIED;

        // 양측 장소 인증 완료 시 QR 즉시 발급 (기존과 동일)
        if (bothVerified) {
            issueQrTokenIfNeeded(meetVerification);
        }

        // 14. 장소 인증 완료 알림
        // [변경] 등록자 인증 → 모든 신청자에게 알림 (그룹 전파 알림)
        //        신청자 인증 → 등록자에게만 알림 (기존과 동일)
        if (isAuthor) {
            // postId 기준 모든 신청자에게 알림
            List<Long> siblingMatchIds = matchService.getMatchIdsByPostId(matchInfo.postId());
            Map<Long, MatchInfoDto> siblingInfos = matchService.getMatchInfos(siblingMatchIds);
            // 각 신청자에게 "등록자가 장소 인증했습니다" 알림 발송
            siblingInfos.values()
                    .forEach(m -> notificationPublisher.sendPlaceVerified(m.applicantId(), matchId));
        } else {
            // 신청자 인증 → 등록자에게만 알림
            notificationPublisher.sendPlaceVerified(postInfo.authorId(), matchId);
        }

        return PlaceVerificationResponseDto.of(meetVerification, distanceMeters, bothVerified);
    }

    // QR 코드 조회 (등록자 전용)
    @Override
    @Transactional
    public QrResponseDto getMeetQr(Long userId, Long matchId) {

        // MeetVerification + MatchInfo + PostInfo 한 번에 조회
        MeetContext ctx = loadMeetContext(matchId);
        MeetVerification meetVerification = ctx.meetVerification();
        PostInfoDto postInfo = ctx.postInfo();

        // 등록자인지 확인 (QR 발급은 등록자만 가능!)
        if (!userId.equals(postInfo.authorId())) {
            throw new MeetException(ErrorCode.QR_NOT_AUTHOR);
        }

        // 장소 인증 완료된 상태인지 체크
        if (meetVerification.getStatus() != VerificationStatus.VERIFIED) {
            throw new MeetException(ErrorCode.QR_PLACE_VERIFICATION_REQUIRED);
        }

        // 장소 인증 완료 시 createPlaceVerification()에서 이미 발급했지만
        // 혹시 발급이 누락된 경우를 대비한 안전망 — 없으면 발급, 있으면 스킵
        issueQrTokenIfNeeded(meetVerification);

        // QR 만료 여부 체크
        if (meetVerification.isQrExpired()) {
            throw new MeetException(ErrorCode.QR_EXPIRED);
        }

        return QrResponseDto.of(matchId, meetVerification);
    }

    // QR 스캔 (신청자 전용)
    @Override
    @Transactional
    public QrScanResponseDto createQrScan(Long userId, Long matchId, QrScanRequestDto requestDto) {

        // matchId로 MeetVerification 조회
        // createQrScan은 신청자 검증만 필요하므로 PostInfo까지는 불필요 — 개별 조회
        MeetVerification meetVerification = meetVerificationRepository.findByMatchId(matchId)
                .orElseThrow(() -> new MeetException(ErrorCode.MEET_VERIFICATION_NOT_FOUND));

        // 신청자 검증을 위해 MatchInfo만 조회
        MatchInfoDto matchInfo = matchService.getMatchInfo(matchId);

        // 신청자인지 확인 (QR 스캔은 신청자만 가능!)
        if (!matchInfo.isApplicant(userId)) {
            throw new MeetException(ErrorCode.SCAN_NOT_APPLICANT);
        }

        // DONE 상태 재스캔 차단
        if (meetVerification.getStatus() == VerificationStatus.DONE) {
            throw new MeetException(ErrorCode.GPS_ALREADY_VERIFIED);
        }

        // 장소 인증 완료 상태인지 체크
        if (meetVerification.getStatus() != VerificationStatus.VERIFIED) {
            throw new MeetException(ErrorCode.QR_PLACE_VERIFICATION_REQUIRED);
        }

        // QR 토큰 만료 여부 체크
        if (meetVerification.isQrExpired()) {
            throw new MeetException(ErrorCode.QR_EXPIRED);
        }

        // QR 토큰 일치 여부 검증
        if (!requestDto.getQrToken().equals(meetVerification.getQrToken())) {
            throw new MeetException(ErrorCode.SCAN_INVALID_QR_TOKEN);
        }

        // 매치 단건 조회 — 신청자 예치금 확인용
        Match match = matchService.getMatchById(matchId);

        // 신청자의 예치금 (환불 응답 DTO에 포함)
        int refundedPoint = match.getApplicantDeposit();

        // 만남 인증 완료 처리
        meetVerification.meetVerifiedDone();

        // 위치 데이터 삭제 (개인정보 최소 수집 원칙)
        userLocationCleanupService.deleteLocationsByMatchId(matchId);

        // 만남 인증 완료 즉시 채팅방을 닫지 않고 "2시간 후 비활성화"를 예약
        chatService.scheduleChatRoomDeactivation(matchInfo.postId());

        // Match 상태 COMPLETED로 변경
        matchService.completeMatch(matchId);

        return QrScanResponseDto.of(matchId, meetVerification, MatchStatus.COMPLETED, refundedPoint);
    }

    // 인증 상태 조회
    @Override
    public MeetVerificationResponseDto getMeetVerification(Long userId, Long matchId) {

        // MeetVerification + MatchInfo + PostInfo 한 번에 조회
        MeetContext ctx = loadMeetContext(matchId);

        // 매칭 당사자 검증
        validateParticipant(userId, ctx.matchInfo(), ctx.postInfo());

        return MeetVerificationResponseDto.of(matchId, ctx.meetVerification());
    }

    // 매칭 생성 시 PENDING 초기화
    @Override
    @Transactional
    public void createPendingVerification(Long matchId) {
        // 매칭 생성 시점에 PENDING 상태로 MeetVerification 레코드 초기화
        meetVerificationRepository.save(MeetVerification.createPending(matchId));
    }

    // GPS 노쇼 배치 판정 — 스케줄러가 주기적으로 호출
    // 판정 대상: PENDING 상태 (양측 GPS 인증이 모두 완료되지 않은 매칭)
    // 판정 시점: meetAt + 20분이 지난 건 (장소 인증 가능 시간이 완전히 지난 후)
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
        BulkMatchContext bulk = loadBulkMatchContext(matchIds);

        for (MeetVerification meetVerification : pendingList) {

            MatchInfoDto matchInfoDto = bulk.matchInfoMap().get(meetVerification.getMatchId());
            if (matchInfoDto == null) {
                // 데이터 정합성이 깨졌을 때를 대비한 방어 — 해당 건만 스킵
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

            // 판정 기준 시각(meetAt + 20분, 장소 인증 종료 시각)이 아직 안 지났으면 이번 배치에서 스킵
            // 다음 스케줄러 실행 때 다시 평가됨
            LocalDateTime deadline = effectiveMeetAt.plusMinutes(NO_SHOW_JUDGE_MINUTES);
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
                chatService.makeReadOnlyChatRoom(matchInfoDto.postId());
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
                chatService.markGuestNoShow(matchInfoDto.postId(), matchInfoDto.applicantId());
                // 발송 플래그 확인 → 중복 발송 방지
                if (!meetVerification.isNoShowWarningSent()) {
                    notificationPublisher.sendNoShowWarning(matchInfoDto.applicantId(), meetVerification.getMatchId());
                    meetVerification.markNoShowWarningSent();
                }

            } else if (!authorVerified) {
                meetVerification.markAuthorNoShow();
                userLocationCleanupService.deleteLocationsByMatchId(meetVerification.getMatchId());
                // HOST_NO_SHOW → 채팅방 전체 READ_ONLY
                chatService.makeReadOnlyChatRoom(matchInfoDto.postId());
                // 발송 플래그 확인 → 중복 발송 방지
                if (!meetVerification.isNoShowWarningSent()) {
                    notificationPublisher.sendNoShowWarning(postInfoDto.authorId(), meetVerification.getMatchId());
                    meetVerification.markNoShowWarningSent();
                }
            }
        }
    }

    // QR 노쇼 배치 판정 — 스케줄러가 주기적으로 호출
    // 판정 대상: VERIFIED 상태 + QR 만료 시간이 지난 매칭
    //           (장소는 도착했는데 30분 안에 QR 인증을 못 한 케이스)
    @Override
    @Transactional
    public void judgeQrNoShow() {

        // VERIFIED 상태 + QR 만료 시간이 지난 verification 전체 조회
        List<MeetVerification> expiresList = meetVerificationRepository
                .findAllByStatusAndQrExpiresAtBefore(VerificationStatus.VERIFIED, LocalDateTime.now());

        // 빈 리스트 방어
        if (expiresList.isEmpty()) {
            return;
        }

        // matchId 목록 추출
        List<Long> matchIds = expiresList.stream()
                .map(MeetVerification::getMatchId)
                .toList();

        // Match 벌크 조회 (judgeQrNoShow는 postInfo를 루프 안에서 개별 조회 — 기존 구조 유지)
        Map<Long, MatchInfoDto> matchInfoDtoMap = matchService.getMatchInfos(matchIds);

        for (MeetVerification meetVerification : expiresList) {

            Long matchId = meetVerification.getMatchId();

            MatchInfoDto matchInfoDto = matchInfoDtoMap.get(matchId);
            if (matchInfoDto == null) {
                // 데이터 정합성 이슈 → 해당 건 스킵
                continue;
            }

            PostInfoDto postInfoDto = postQueryService.getPostInfo(matchInfoDto.postId());
            if (postInfoDto == null) {
                continue;
            }

            // 두 사람이 지금도 반경 안에 있는지 확인
            // true  → 최근 15초 이내 위치 업데이트 + 반경 60m 이내 → 아직 현장에 있음
            // false → 위치 없음 or 오래된 위치 or 반경 밖 → 자리를 뜬 것으로 판단
            boolean authorInRange = userLocationService.isFreshLocationWithinRadius(
                    matchId,
                    postInfoDto.authorId(),
                    postInfoDto.placeLat(),
                    postInfoDto.placeLng(),
                    NO_SHOW_RADIUS_METERS,
                    LOCATION_FRESHNESS_SECONDS
            );

            boolean applicantInRange = userLocationService.isFreshLocationWithinRadius(
                    matchId,
                    matchInfoDto.applicantId(),
                    postInfoDto.placeLat(),
                    postInfoDto.placeLng(),
                    NO_SHOW_RADIUS_METERS,
                    LOCATION_FRESHNESS_SECONDS
            );

            // 위치 기반 노쇼 판정 분기
            if (authorInRange && !applicantInRange) {
                // 등록자는 현장에 있고 신청자가 없는 경우 → GUEST_NO_SHOW 예정
                meetVerification.markApplicantNoShow();
                // GUEST_NO_SHOW → 해당 신청자만 NO_SHOW 처리
                chatService.markGuestNoShow(matchInfoDto.postId(), matchInfoDto.applicantId());
                // 발송 플래그 확인 → 중복 발송 방지
                if (!meetVerification.isNoShowWarningSent()) {
                    notificationPublisher.sendNoShowWarning(matchInfoDto.applicantId(), matchId);
                    meetVerification.markNoShowWarningSent();
                }

            } else if (!authorInRange && applicantInRange) {
                // 신청자는 현장에 있고 등록자가 없는 경우 → HOST_NO_SHOW 예정
                meetVerification.markAuthorNoShow();
                // HOST_NO_SHOW → 채팅방 전체 READ_ONLY
                chatService.makeReadOnlyChatRoom(matchInfoDto.postId());
                // 발송 플래그 확인 → 중복 발송 방지
                if (!meetVerification.isNoShowWarningSent()) {
                    notificationPublisher.sendNoShowWarning(postInfoDto.authorId(), matchId);
                    meetVerification.markNoShowWarningSent();
                }

            } else if (!authorInRange) {
                // 여기 도달 시점 = authorInRange=false, applicantInRange=false 확정
                // 둘 다 현장에 없는 경우 → BOTH_NO_SHOW 예정
                meetVerification.markBothNoShow();
                // BOTH_NO_SHOW → 채팅방 전체 READ_ONLY
                chatService.makeReadOnlyChatRoom(matchInfoDto.postId());
                // 발송 플래그 확인 → 중복 발송 방지
                if (!meetVerification.isNoShowWarningSent()) {
                    notificationPublisher.sendNoShowWarning(postInfoDto.authorId(), matchId);
                    notificationPublisher.sendNoShowWarning(matchInfoDto.applicantId(), matchId);
                    meetVerification.markNoShowWarningSent();
                }

            } else {
                // 여기 도달 시점 = authorInRange=true, applicantInRange=true 확정
                // 정책 시나리오 3-6: QR 만료 시각까지 둘 다 현장에 있었는데 QR 인증 미완료
                // → 노쇼 아님, 귀책 없음 → 매칭 취소 + 양측 전액 환불
                matchService.cancelMatchBySystem(matchId);
            }

            // 위치 정보 삭제 (개인정보 최소 수집 원칙)
            userLocationCleanupService.deleteLocationsByMatchId(meetVerification.getMatchId());
        }
    }

    // 노쇼 확정 — 스케줄러가 주기적으로 호출
    // 노쇼 예정 상태에서 24시간이 지나면 최종 확정 처리
    @Override
    @Transactional
    public void judgeNoShowConfirmed() {

        // 노쇼 확정 기준 시각 계산
        // noShowDecidedAt이 이 시각보다 이전인 건 = 24시간이 지난 건
        LocalDateTime deadline = LocalDateTime.now().minusHours(NO_SHOW_CONFIRM_HOURS);

        // NO_SHOW 상태이면서 noShowDecidedAt이 24시간 이전인 건 전체 조회
        List<MeetVerification> noShowList = meetVerificationRepository
                .findAllByStatusInAndNoShowDecidedAtBefore(NO_SHOW_STATUSES, deadline);

        // 처리할 건이 없으면 조기 종료
        if (noShowList.isEmpty()) {
            return;
        }

        // matchId 목록 추출
        List<Long> matchIds = noShowList.stream()
                .map(MeetVerification::getMatchId)
                .toList();

        // Match + Post 벌크 조회 (N+1 방지)
        BulkMatchContext bulk = loadBulkMatchContext(matchIds);

        // 관리자가 아직 검토 중인 이의제기가 있는 matchId Set 조회
        Set<Long> activeDisputeMatchIds = disputeQueryService.getMatchIdsWithActiveDispute(matchIds);

        for (MeetVerification meetVerification : noShowList) {

            Long matchId = meetVerification.getMatchId();

            // 이의제기가 아직 검토 중인 건은 확정 처리 대상에서 제외
            // 관리자가 ACCEPTED / REJECTED 판정을 내릴 때까지 24시간 타이머를 멈춤
            // 판정이 나면 AdminDisputeService에서 직접 confirmNoShow()를 호출
            if (activeDisputeMatchIds.contains(matchId)) {
                continue;
            }

            // 데이터 정합성 방어 — Match 또는 Post 정보가 없으면 해당 건만 스킵
            MatchInfoDto matchInfoDto = bulk.matchInfoMap().get(matchId);
            if (matchInfoDto == null) continue;

            PostInfoDto postInfoDto = bulk.postInfoMap().get(matchInfoDto.postId());
            if (postInfoDto == null) continue;

            VerificationStatus status = meetVerification.getStatus();

            // 발송 플래그 확인 → 중복 발송 방지
            if (!meetVerification.isNoShowConfirmedSent()) {
                // 노쇼 상태에 따라 Match 도메인에 확정 처리 위임
                if (status == VerificationStatus.BOTH_NO_SHOW) {
                    // 17. 노쇼 확정 알림 - 관련 사용자 양측에게
                    // 양측 모두 노쇼 확정 — 양측 예치금 전부 몰수
                    matchService.markBothNoShow(matchId);
                    notificationPublisher.sendNoShowConfirmed(postInfoDto.authorId(), matchId);
                    notificationPublisher.sendNoShowConfirmed(matchInfoDto.applicantId(), matchId);

                } else if (status == VerificationStatus.GUEST_NO_SHOW) {
                    // 17. 노쇼 확정 알림 - 관련 사용자 양측에게
                    // 신청자만 노쇼 확정 — 신청자 예치금 몰수 + 등록자 환급
                    matchService.markApplicantNoShow(matchId);
                    notificationPublisher.sendNoShowConfirmed(matchInfoDto.applicantId(), matchId);

                } else if (status == VerificationStatus.HOST_NO_SHOW) {
                    // 17. 노쇼 확정 알림 - 관련 사용자 양측에게
                    // 등록자만 노쇼 확정 — 등록자 예치금 몰수 + 신청자 환급
                    matchService.markAuthorNoShow(matchId);
                    notificationPublisher.sendNoShowConfirmed(postInfoDto.authorId(), matchId);
                }

                // 알림 발송 완료 플래그 저장
                meetVerification.markNoShowConfirmedSent();
            }

            // 처리 완료 — 다음 배치에서 중복 실행 방지
            meetVerification.confirmNoShow();
        }
    }

    // 노쇼 후보군 조회 (Admin 전용)
    @Override
    public Page<MeetVerification> getNoShowCandidates(Pageable pageable) {
        return meetVerificationRepository.findAllByStatusIn(NO_SHOW_STATUSES, pageable);
    }

    // 만남 시간 연장 요청
    @Override
    @Transactional
    public CreateMeetExtensionResponseDto createMeetExtension(Long userId, Long matchId) {

        // MeetVerification + MatchInfo + PostInfo 한 번에 조회
        MeetContext ctx = loadMeetContext(matchId);
        MeetVerification meetVerification = ctx.meetVerification();
        MatchInfoDto matchInfoDto = ctx.matchInfo();
        PostInfoDto postInfoDto = ctx.postInfo();

        // 매칭 당사자 확인
        validateParticipant(userId, matchInfoDto, postInfoDto);

        // MATCH 상태 확인 (노쇼 판정 이후 or 완료된 매칭엔 연장 불가)
        if (matchInfoDto.status() != MatchStatus.MATCHED) {
            throw new MeetException(ErrorCode.MEET_EXTEND_MATCH_NOT_MATCHED);
        }

        // 연장 요청은 약속시간 5분 전까지만 가능
        if (!LocalDateTime.now().isBefore(postInfoDto.meetAt().minusMinutes(EXTENSION_TIMEOUT_MINUTES))) {
            throw new MeetException(ErrorCode.MEET_EXTEND_BEFORE_MEET_AT);
        }

        // ACCEPTED → 이미 1회 연장 성공 → 영영 불가
        // REJECTED → 거절됨 → 영영 불가
        if (meetVerification.isExtended() || meetVerification.getExtensionStatus() == ExtensionStatus.REJECTED) {
            throw new MeetException(ErrorCode.MEET_EXTEND_ALREADY_ACCEPTED);
        }

        // 진행 중인 요청(REQUESTED) 체크 전에 만료 여부를 먼저 확인
        // 스케줄러가 EXPIRED로 전환하지 않은 타이밍에도 재요청을 허용하기 위함
        if (meetVerification.getExtensionStatus() == ExtensionStatus.REQUESTED) {
            if (meetVerification.isExtensionExpired(EXTENSION_TIMEOUT_MINUTES)) {
                // 5분 타임아웃이 지났으면 → 즉시 EXPIRED 처리 후 요청 허용
                meetVerification.expireExtension();
            } else {
                // 아직 5분 안 지났으면 진행 중인 요청이 있으므로 차단
                throw new MeetException(ErrorCode.MEET_EXTEND_ALREADY_REQUESTED);
            }
        }

        // 연장 요청 처리
        meetVerification.requestExtension(userId);

        // 상대방에게 연장 요청 알림 발송
        Long opponentId = userId.equals(postInfoDto.authorId())
                ? matchInfoDto.applicantId()
                : postInfoDto.authorId();
        // 18. 만남 시간 연장 요청 알림 - 만남 상대방에게
        notificationPublisher.sendMeetExtendRequested(opponentId, matchId);

        // 연장 타임아웃 ZSet 예약
        // score = 요청 시각 + 5분 Unix Timestamp
        // member = meetVerification ID (스케줄러가 꺼내서 만료 처리)
        redisTemplate.opsForZSet().add(
                MeetRedisZSetKeys.EXTENSION_TIMEOUT,
                String.valueOf(meetVerification.getId()),
                LocalDateTime.now().plusMinutes(EXTENSION_TIMEOUT_MINUTES).toEpochSecond(KST)
        );

        // 요청자 닉네임 조회
        String requesterNickname = userService.getUserInfo(userId).nickname();

        return CreateMeetExtensionResponseDto.of(meetVerification, requesterNickname, postInfoDto.meetAt());
    }

    // 만남 시간 연장 수락
    @Override
    @Transactional
    public AcceptMeetExtensionResponseDto acceptMeetExtension(Long userId, Long matchId) {

        // MeetVerification + MatchInfo + PostInfo 한 번에 조회
        MeetContext ctx = loadMeetContext(matchId);
        MeetVerification meetVerification = ctx.meetVerification();
        MatchInfoDto matchInfoDto = ctx.matchInfo();
        PostInfoDto postInfoDto = ctx.postInfo();

        // 매칭 당사자 확인
        validateParticipant(userId, matchInfoDto, postInfoDto);

        // 연장 요청 만료 여부 확인 + 즉시 만료 처리
        validateExtensionNotExpired(meetVerification);

        // 응답 가능한 요청이 있는지 확인
        if (meetVerification.getExtensionStatus() != ExtensionStatus.REQUESTED) {
            throw new MeetException(ErrorCode.MEET_EXTEND_NO_ACTIVE_REQUEST);
        }

        // 본인 요청은 본인이 수락 불가
        if (userId.equals(meetVerification.getExtensionRequesterId())) {
            throw new MeetException(ErrorCode.MEET_EXTEND_SELF_RESPONSE);
        }

        // 수락 처리 → meetAt + 10분을 extendedMeetAt에 저장
        meetVerification.acceptExtension(postInfoDto.meetAt(), EXTENSION_MINUTES);

        // QR 만료 시각도 10분 연장
        meetVerification.extendQrExpiry(EXTENSION_MINUTES);

        // 19. 만남 시간 연장 수락 알림 - 연장 요청자에게
        notificationPublisher.sendMeetExtendAccepted(meetVerification.getExtensionRequesterId(), matchId);

        // 수락 시 타임아웃 예약 제거 (더 이상 만료 처리 불필요)
        redisTemplate.opsForZSet().remove(
                MeetRedisZSetKeys.EXTENSION_TIMEOUT,
                String.valueOf(meetVerification.getId())
        );

        return AcceptMeetExtensionResponseDto.of(meetVerification, postInfoDto.meetAt());
    }

    // 만남 시간 연장 거절
    @Override
    @Transactional
    public RejectMeetExtensionResponseDto rejectMeetExtension(Long userId, Long matchId) {

        // MeetVerification + MatchInfo + PostInfo 한 번에 조회
        MeetContext ctx = loadMeetContext(matchId);
        MeetVerification meetVerification = ctx.meetVerification();
        MatchInfoDto matchInfoDto = ctx.matchInfo();
        PostInfoDto postInfoDto = ctx.postInfo();

        // 매칭 당사자 확인
        validateParticipant(userId, matchInfoDto, postInfoDto);

        // 연장 요청 만료 여부 확인 + 즉시 만료 처리
        validateExtensionNotExpired(meetVerification);

        // 응답 가능한 요청 있는지 확인
        if (meetVerification.getExtensionStatus() != ExtensionStatus.REQUESTED) {
            throw new MeetException(ErrorCode.MEET_EXTEND_NO_ACTIVE_REQUEST);
        }

        // 본인 요청은 본인이 거절 불가
        if (userId.equals(meetVerification.getExtensionRequesterId())) {
            throw new MeetException(ErrorCode.MEET_EXTEND_SELF_RESPONSE);
        }

        // 거절 처리
        meetVerification.rejectExtension();

        // 20. 만남 시간 연장 거절 알림 - 연장 요청자에게
        notificationPublisher.sendMeetExtendRejected(meetVerification.getExtensionRequesterId(), matchId);

        // 거절 시 타임아웃 예약 제거
        redisTemplate.opsForZSet().remove(
                MeetRedisZSetKeys.EXTENSION_TIMEOUT,
                String.valueOf(meetVerification.getId())
        );

        return RejectMeetExtensionResponseDto.from(meetVerification);
    }

    // 연장 상태 조회
    @Override
    public GetMeetExtensionResponseDto getMeetExtension(Long userId, Long matchId) {

        // MeetVerification + MatchInfo + PostInfo 한 번에 조회
        MeetContext ctx = loadMeetContext(matchId);
        MeetVerification meetVerification = ctx.meetVerification();
        MatchInfoDto matchInfoDto = ctx.matchInfo();
        PostInfoDto postInfoDto = ctx.postInfo();

        // 매칭 당사자 확인
        validateParticipant(userId, matchInfoDto, postInfoDto);

        // NONE 상태면 아직 요청자 없음 → 닉네임 null 처리
        String requesterNickname = null;
        if (meetVerification.getExtensionRequesterId() != null) {
            requesterNickname = userService.getUserInfo(meetVerification.getExtensionRequesterId()).nickname();
        }

        return GetMeetExtensionResponseDto.of(meetVerification, requesterNickname, postInfoDto.meetAt(), userId);
    }

    // 연장 요청 타임아웃 일괄 처리 — 스케줄러가 주기적으로 호출
    @Override
    @Transactional
    public void expireTimeoutExtensions() {

        // 요청 시각 + 5분이 지난 REQUESTED 상태 목록 조회
        LocalDateTime expireThreshold = LocalDateTime.now().minusMinutes(EXTENSION_TIMEOUT_MINUTES);

        List<MeetVerification> expiredList = meetVerificationRepository
                .findAllByExtensionStatusAndExtensionRequestedAtBefore(ExtensionStatus.REQUESTED, expireThreshold);

        if (expiredList.isEmpty()) {
            return;
        }

        // 일괄 EXPIRED 처리 (더티체킹으로 자동 업데이트)
        expiredList.forEach(mv -> {
            mv.expireExtension();

            // 만료 처리 완료 → ZSet에서 제거 (중복 처리 방지)
            redisTemplate.opsForZSet().remove(
                    MeetRedisZSetKeys.EXTENSION_TIMEOUT,
                    String.valueOf(mv.getId())
            );

            // 21. 만남 시간 연장 만료 알림 - 연장 요청자에게
            notificationPublisher.sendMeetExtendExpired(mv.getExtensionRequesterId(), mv.getMatchId());
        });
    }

    // matchId로 MeetVerification 단건 조회 (외부 도메인 사용)
    @Override
    public MeetVerification getByMatchId(Long matchId) {
        return meetVerificationRepository.findByMatchId(matchId)
                .orElseThrow(() -> new MeetException(ErrorCode.MEET_VERIFICATION_NOT_FOUND));
    }

    // ======== private 헬퍼 메서드 ==============================================

    // QR 토큰 발급 — 중복 발급 방지 포함
    // 호출 시점: 양측 GPS 인증 완료 직후
    private void issueQrTokenIfNeeded(MeetVerification meetVerification) {

        // 이미 발급된 토큰이 있으면 스킵 — 멱등성 보장 (여러 번 호출해도 1번만 발급됨)
        if (meetVerification.getQrToken() != null) {
            return;
        }

        // 양측 GPS 인증이 완료되지 않았으면 스킵 (정상적으로는 도달하지 않아야 하는 방어 코드)
        if (meetVerification.getAuthorPlaceVerifiedAt() == null
                || meetVerification.getApplicantPlaceVerifiedAt() == null) {
            return;
        }

        // QR 만료 시각 = 양측 중 더 나중에 인증한 시각 + 30분
        LocalDateTime placeVerifiedCompletedAt = meetVerification.getAuthorPlaceVerifiedAt()
                .isAfter(meetVerification.getApplicantPlaceVerifiedAt())
                ? meetVerification.getAuthorPlaceVerifiedAt()
                : meetVerification.getApplicantPlaceVerifiedAt();

        LocalDateTime expiresAt = placeVerifiedCompletedAt.plusMinutes(QR_TOKEN_VALIDITY_MINUTES);
        String qrToken = "hp_qr_" + UUID.randomUUID().toString().replace("-", "");

        meetVerification.issueQrToken(qrToken, expiresAt);
    }

    // ① MeetVerification + MatchInfo + PostInfo 한 번에 조회
    // 사용 위치: createPlaceVerification, getMeetQr, getMeetVerification,
    //            createMeetExtension, acceptMeetExtension, rejectMeetExtension, getMeetExtension
    private record MeetContext(
            MeetVerification meetVerification,
            MatchInfoDto matchInfo,
            PostInfoDto postInfo
    ) {}

    private MeetContext loadMeetContext(Long matchId) {
        // matchId로 MeetVerification 조회, 없으면 예외
        MeetVerification meetVerification = meetVerificationRepository.findByMatchId(matchId)
                .orElseThrow(() -> new MeetException(ErrorCode.MEET_VERIFICATION_NOT_FOUND));
        // matchId로 매칭 정보 조회
        MatchInfoDto matchInfo = matchService.getMatchInfo(matchId);
        // 매칭 정보에서 postId를 꺼내 게시글 정보 조회
        PostInfoDto postInfo = postQueryService.getPostInfo(matchInfo.postId());
        return new MeetContext(meetVerification, matchInfo, postInfo);
    }

    // ② 매칭 당사자 검증
    // 사용 위치: createPlaceVerification, getMeetVerification, createMeetExtension,
    //            acceptMeetExtension, rejectMeetExtension, getMeetExtension
    private void validateParticipant(Long userId, MatchInfoDto matchInfo, PostInfoDto postInfo) {
        // 등록자(authorId) 또는 신청자(applicantId)가 아니면 예외
        if (!matchInfo.isParticipant(userId, postInfo.authorId())) {
            throw new MeetException(ErrorCode.MATCH_NOT_PARTICIPANT);
        }
    }

    // ③ 연장 만료 여부 확인 + 즉시 만료 처리
    // 사용 위치: acceptMeetExtension, rejectMeetExtension
    private void validateExtensionNotExpired(MeetVerification meetVerification) {
        // REQUESTED 상태에서 5분 타임아웃이 지났으면 즉시 EXPIRED 처리 후 예외
        if (meetVerification.getExtensionStatus() == ExtensionStatus.REQUESTED
                && meetVerification.isExtensionExpired(EXTENSION_TIMEOUT_MINUTES)) {
            meetVerification.expireExtension();
            throw new MeetException(ErrorCode.MEET_EXTEND_EXPIRED);
        }
    }

    // ④ matchId 목록으로 MatchInfo + PostInfo 벌크 조회
    // 사용 위치: judgeGpsNoShow, judgeNoShowConfirmed
    private record BulkMatchContext(
            Map<Long, MatchInfoDto> matchInfoMap,
            Map<Long, PostInfoDto> postInfoMap
    ) {}

    private BulkMatchContext loadBulkMatchContext(List<Long> matchIds) {
        // Match 도메인 벌크 조회 (N+1 방지)
        Map<Long, MatchInfoDto> matchInfoMap = matchService.getMatchInfos(matchIds);
        // Match에서 postId만 뽑아서 Post 도메인 벌크 조회
        List<Long> postIds = matchInfoMap.values().stream()
                .map(MatchInfoDto::postId)
                .distinct()
                .toList();
        Map<Long, PostInfoDto> postInfoMap = postQueryService.getPostInfos(postIds);
        return new BulkMatchContext(matchInfoMap, postInfoMap);
    }

    // 등록자 GPS 인증을 같은 postId의 모든 MeetVerification에 전파
    private void propagateAuthorVerification(Long postId) {

        // 같은 postId에 속한 모든 matchId 목록 조회
        List<Long> siblingMatchIds = matchService.getMatchIdsByPostId(postId);

        for (Long siblingMatchId : siblingMatchIds) {

            // 각 matchId의 MeetVerification 조회
            // 데이터 정합성 문제로 없는 경우 방어 — 해당 건만 스킵
            MeetVerification mv = meetVerificationRepository.findByMatchId(siblingMatchId)
                    .orElse(null);
            if (mv == null) continue;

            // 이미 등록자 인증이 완료된 건은 중복 처리 방지
            // (등록자가 두 번 호출하거나 스케줄러 중복 실행 시 멱등성 보장)
            if (mv.isAuthorPlaceVerified()) continue;

            // 등록자 GPS 인증 처리
            // 내부 updateToVerifiedIfDone(): 신청자도 인증됐으면 VERIFIED로 자동 전환
            mv.verifyAuthorPlace();
        }
    }
}