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

    // GPS 오차범위까지 고려한 인증 반경
    private static final double PLACE_VERIFICATION_RADIUS_METERS = 60.0;
    // QR 토큰 TTL - 장소 인증 완료 시점 + 30분
    private static final long QR_TOKEN_VALIDITY_MINUTES = 30;
    // 장소 인증 가능 시간 : 만남 시간 15분전 ~ 1시간
    private static final long VERIFICATION_BEFORE_MINUTES = 15;
    // 화면 활성 시간
    private static final long VERIFICATION_AFTER_MINUTES = 60;
    // 노쇼 판정 기준 : GPS -> meetAt + 30분
    private static final long NO_SHOW_JUDGE_MINUTES = 30;
    // 연장 요청 타임아웃 : 요청 시각 + 5분
    private static final long EXTENSION_TIMEOUT_MINUTES = 5;
    // 연장 시간
    private static final long EXTENSION_MINUTES = 15;
    // 노쇼 확정까지 이의제기 가능 시간: 24시간
    private static final long NO_SHOW_CONFIRM_HOURS = 24;
    // 5초마다 위치 업데이트 정책을 기준으로 안전 여유 값 15초
    private static final long LOCATION_FRESHNESS_SECONDS = 15;
    // GPS 오차범위 고려한 노쇼 범위
    private static final double NO_SHOW_RADIUS_METERS = 60.0;

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

        // matchId로 MeetVerification 조회
        MeetVerification meetVerification = meetVerificationRepository.findByMatchId(matchId)
                .orElseThrow(() -> new MeetException(ErrorCode.MEET_VERIFICATION_NOT_FOUND));

        // MatchInfoDto 조회
        MatchInfoDto matchInfo = matchService.getMatchInfo(matchId);

        // PostInfoDto 조회
        // match -> postId -> post 순서대로 (Match에는 authorId 없음)
        PostInfoDto postInfo = postQueryService.getPostInfo(matchInfo.postId());

        // 매칭 당사자가 맞는지 검증 (등록자 or 신청자인지)
        if (!matchInfo.isParticipant(userId, postInfo.authorId())) {
            throw new MeetException(ErrorCode.MATCH_NOT_PARTICIPANT);
        }

        // 노쇼 예정, 이의제기 중, 노쇼 확정 상태에서는 GPS 재인증 차단
        if (NO_SHOW_STATUSES.contains(meetVerification.getStatus())
                || meetVerification.getStatus() == VerificationStatus.DISPUTE
                || meetVerification.getStatus() == VerificationStatus.NO_SHOW_CONFIRMED) {
            throw new MeetException(ErrorCode.GPS_NOT_VERIFICATION_TIME);
        }

            LocalDateTime now = LocalDateTime.now();
        // 시작 시간은 원래 약속시간 기준 15분 전으로 고정
        LocalDateTime verificationStartTime = postInfo.meetAt().minusMinutes(VERIFICATION_BEFORE_MINUTES);

        // 연장 수락 시 실제 약속시간이 바뀌므로 종료 시각도 그에 맞게 조정
        // 연장 미사용 → 원래 meetAt + 60분
        // 연장 수락 → extendedMeetAt(meetAt + 15분) + 60분
        // extendedMeetAt은 acceptExtension() 시점에 채워지므로 null이면 미사용을 의미
        LocalDateTime effectiveMeetAt = meetVerification.getExtendedMeetAt()
                != null ? meetVerification.getExtendedMeetAt() : postInfo.meetAt();

        LocalDateTime verificationEndTime = effectiveMeetAt.plusMinutes(VERIFICATION_AFTER_MINUTES);

        if (now.isBefore(verificationStartTime) || now.isAfter(verificationEndTime)) {
            throw new MeetException(ErrorCode.GPS_NOT_VERIFICATION_TIME);
        }

        // 이미 본인이 인증 완료했는지 체크
        // userId 기반으로 등록저/신청자 구분하여 각각 체크
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

        // 반경 60m(오차 범위 포함) 벗어났는지 체크
        if (distanceMeters > PLACE_VERIFICATION_RADIUS_METERS) {
            // 32번 알림 - 반경 이탈 경고 알림 발송
            notificationPublisher.sendGpsOutOfRange(userId, matchId);
            throw new MeetException(ErrorCode.GPS_OUT_OF_RANGE);
        }

        // userId 기반으로 등록자/신청자 구분하여 각각 인증 처리
        if (isAuthor) {
            meetVerification.verifyAuthorPlace();
        } else {
            meetVerification.verifyApplicantPlace();
        }

        boolean bothVerified = meetVerification.getStatus() == VerificationStatus.VERIFIED;

        // 양측 장소 인증이 완료된 순간 QR을 즉시 발급
        // 등록자가 먼저 인증하든 신청자가 먼저 하든 마지막 인증 완료 시점에 한 번만 발급됨
        // (내부에서 이미 토큰이 있으면 스킵 — 중복 발급 방지)
        if (bothVerified) {
            issueQrTokenIfNeeded(meetVerification);
        }

        // 4번 알림 - 상대방에게 장소 인증 완료 알림 발송
        // isAuthor면 상대방은 신청자(applicantId), 아니면 등록자(authorId)
        Long opponentId = isAuthor ? matchInfo.applicantId() : postInfo.authorId();
        notificationPublisher.sendPlaceVerified(opponentId, matchId);


        return PlaceVerificationResponseDto.of(meetVerification, distanceMeters, bothVerified);
    }

    @Override
    @Transactional
    public QrResponseDto getMeetQr(Long userId, Long matchId) {
        // matchId 조회
        MeetVerification meetVerification = meetVerificationRepository.findByMatchId(matchId)
                .orElseThrow(() -> new MeetException(ErrorCode.MEET_VERIFICATION_NOT_FOUND));

        // MatchInfoDto -> PostInfoDto 순으로 타서 authorId 획득
        MatchInfoDto matchInfo = matchService.getMatchInfo(matchId);
        PostInfoDto postInfo = postQueryService.getPostInfo(matchInfo.postId());

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

        // 만약 QR 만료면 예외
        if (meetVerification.isQrExpired()) {
            throw new MeetException(ErrorCode.QR_EXPIRED);
        }

        return QrResponseDto.of(matchId, meetVerification);
    }

    // QR 스캔
    @Override
    @Transactional
    public QrScanResponseDto createQrScan(Long userId, Long matchId, QrScanRequestDto requestDto) {

        // matchId로 조회 MeetVerification 조회
        MeetVerification meetVerification = meetVerificationRepository.findByMatchId(matchId)
                .orElseThrow(() -> new MeetException(ErrorCode.MEET_VERIFICATION_NOT_FOUND));

        // MatchInfoDto 조회로 신청자 검증
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

        // 매치 단건 조회
        Match match = matchService.getMatchById(matchId);

        // refundedPoint 실제 값 연결
        // 신청자의 예치금
        int refundedPoint = match.getApplicantDeposit();

        // 만남 인증 완료 처리
        meetVerification.meetVerifiedDone();

        // 위치 데이터 삭제
        userLocationCleanupService.deleteLocationsByMatchId(matchId);

        // 만남 인증 완료 되는 순간 채팅방 비활성화 예약
        Long postId = matchInfo.postId();

        // 만남 인증 완료 즉시 채팅방을 닫지 않고 "2시간 후 비활성화"를 예약
        chatService.scheduleChatRoomDeactivation(postId);

        // Match 상태 COMPLETED로 변경
        matchService.completeMatch(matchId);

        return QrScanResponseDto.of(matchId, meetVerification, MatchStatus.COMPLETED, refundedPoint);
    }

    // QR 인증 상태 조회
    @Override
    public MeetVerificationResponseDto getMeetVerification(Long userId, Long matchId) {

        MeetVerification meetVerification = meetVerificationRepository.findByMatchId(matchId)
                .orElseThrow(() -> new MeetException(ErrorCode.MEET_VERIFICATION_NOT_FOUND));

        // MatchInfoDto → PostInfoDto 순으로 타서 authorId 획득
        MatchInfoDto matchInfo = matchService.getMatchInfo(matchId);
        PostInfoDto postInfo = postQueryService.getPostInfo(matchInfo.postId());

        // 매칭 당사자 검증
        if (!matchInfo.isParticipant(userId, postInfo.authorId())) {
            throw new MeetException(ErrorCode.MATCH_NOT_PARTICIPANT);
        }

        return MeetVerificationResponseDto.of(matchId, meetVerification);
    }

    @Override
    @Transactional
    public void createPendingVerification(Long matchId) {
        // 매칭 생성 시점에 PENDING 상태로 MeetVerification 레코드 초기화
        meetVerificationRepository.save(MeetVerification.createPending(matchId));
    }

    // GPS 노쇼 배치 판정 — 스케줄러가 주기적으로 호출
    // 판정 대상: PENDING 상태 (양측 GPS 인증이 모두 완료되지 않은 매칭)
    // 판정 시점: meetAt + 30분이 지난 건 (인증 가능 시간이 완전히 지난 후)
    @Override
    @Transactional
    public void judgeGpsNoShow() {

        LocalDateTime now = LocalDateTime.now();

        // PENDING 상태인 MeetVerification 전체 조회 (쿼리 1번)
        // PENDING = 양측 GPS 장소 인증이 모두 완료되지 않은 매칭
        List<MeetVerification> pendingList = meetVerificationRepository.findAllByStatus(VerificationStatus.PENDING);

        // 빈 리스트 방어
        // 후속 IN쿼리에 빈 컬렉션이 들어가면 일부 DB에서 SQL 문법 오류가 발생함!
        // 불필요한 외부 서비스 호출도 미리 차단
        if (pendingList.isEmpty()) {
            return;
        }

        // 각 verification에서 matchId만 추출
        List<Long> matchIds = pendingList.stream()
                .map(MeetVerification::getMatchId)
                .toList();

        // Match 도메인에 한 번에 조회 요청 (벌크 조회)
        Map<Long, MatchInfoDto> matchInfoDtoMap = matchService.getMatchInfos(matchIds);

        // 위에서 받은 MatchInfo들에서 postId만 추출
        List<Long> postIds = matchInfoDtoMap.values()
                .stream()
                .map(MatchInfoDto::postId)
                .toList();

        // Post 도메인에 한 번에 조회 요청
        Map<Long, PostInfoDto> postInfoDtoMap = postQueryService.getPostInfos(postIds);

        for (MeetVerification meetVerification : pendingList) {
            // 만약, 누락 된 matchId가 Map에 없을 수 있으므로, 이러한 경우 null 반환
            MatchInfoDto matchInfoDto = matchInfoDtoMap.get(meetVerification.getMatchId());
            if (matchInfoDto == null) {
                // 데이터 정합성이 깨졌을 때를 대비한 방어 -> 해당 건만 스킵
                continue;
            }

            Long currentPostId = matchInfoDto.postId();
            PostInfoDto postInfoDto = postInfoDtoMap.get(currentPostId);
            if (postInfoDto == null) {
                continue;
            }

            // 연장 수락된 매칭은 원래 meetAt이 아닌 extendedMeetAt을 기준으로 노쇼 판정
            // 연장 안 했으면 extendedMeetAt이 null이므로 원래 meetAt 사용
            LocalDateTime effectiveMeetAt = meetVerification.getExtendedMeetAt()
                    != null ? meetVerification.getExtendedMeetAt() : postInfoDto.meetAt();

            // 판정 기준 시각(meetAt + 30분)이 아직 안 지났으면 이번 배치에서 스킵
            // 다음 스케줄러 실행 때 다시 평가됨
            LocalDateTime deadline = effectiveMeetAt.plusMinutes(NO_SHOW_JUDGE_MINUTES);
            if (now.isBefore(deadline)) {
                continue;
            }

            // 양측 GPS 인증 여부 확인
            boolean authorVerified = meetVerification.isAuthorPlaceVerified();
            boolean applicantVerified = meetVerification.isApplicantPlaceVerified();

            // 노쇼 판정 분기
            // 양측 모두 GPS 미인증 -> Both_No_Show
            if (!authorVerified && !applicantVerified) {
                meetVerification.markBothNoShow();
                userLocationCleanupService.deleteLocationsByMatchId(meetVerification.getMatchId());
                chatService.makeReadOnlyChatRoom(currentPostId);

                // 양측 모두 노쇼 예정 상태 진입 → Warning 발송
                notificationPublisher.sendNoShowWarning(postInfoDto.authorId(), meetVerification.getMatchId());
                notificationPublisher.sendNoShowWarning(matchInfoDto.applicantId(), meetVerification.getMatchId());

            } else if (authorVerified && !applicantVerified) {
                // 신청자가 노쇼 -> GUEST_NO_SHOW
                meetVerification.markApplicantNoShow();
                userLocationCleanupService.deleteLocationsByMatchId(meetVerification.getMatchId());
                chatService.makeReadOnlyChatRoom(currentPostId);
                // 신청자에게 노쇼 예정 알림 발송
                notificationPublisher.sendNoShowWarning(matchInfoDto.applicantId(), meetVerification.getMatchId());
            } else if (!authorVerified) {
                // 등록자 노쇼 -> HOST_NO_SHOW
                meetVerification.markAuthorNoShow();
                userLocationCleanupService.deleteLocationsByMatchId(meetVerification.getMatchId());
                chatService.makeReadOnlyChatRoom(currentPostId);
                // 등록자에게 노쇼 예정 알림 발송
                notificationPublisher.sendNoShowWarning(postInfoDto.authorId(), meetVerification.getMatchId());
            }
        }
    }

    // QR 노쇼 배치 판정 — 스케줄러가 주기적으로 호출
    // 판정 대상: VERIFIED 상태(양측 GPS 인증 완료) + QR 만료 시간이 지난 매칭 = 장소는 도착했는데 30분 안에 QR 인증을 못 한 케이스
    @Override
    @Transactional
    public void judgeQrNoShow() {

        // VERIFIED 상태 + QR 만료 시간이 지난 verification 전체 조회
        // VERIFIED -> 양측 GPS 장소 인증 완료된 상태
        List<MeetVerification> expiresList = meetVerificationRepository
                .findAllByStatusAndQrExpiresAtBefore(VerificationStatus.VERIFIED, LocalDateTime.now());

        // 빈 리스트 방어 -> 불필요한 반복문 진입 차단
        if (expiresList.isEmpty()) {
            return;
        }

        // matchId 목록 추출
        List<Long> matchIds = expiresList.stream()
                .map(MeetVerification::getMatchId)
                .toList();

        Map<Long, MatchInfoDto> matchInfoDtoMap = matchService.getMatchInfos(matchIds);

        // QR단계에서 만료된 건 -> 신청자가 스캔을 안 한 케이스 -> 일괄 신청자 노쇼
        for (MeetVerification meetVerification : expiresList) {

            Long matchId = meetVerification.getMatchId();

            // matchId → MatchInfoDto → postId 추출
            MatchInfoDto matchInfoDto = matchInfoDtoMap.get(matchId);
            if (matchInfoDto == null) {
                // 데이터 정합성 이슈 → 해당 건 스킵
                continue;
            }

            Long postId = matchInfoDto.postId();

            PostInfoDto postInfoDto = postQueryService.getPostInfo(postId);
            if (postInfoDto == null) {
                continue;
            }

            // 두 사람이 지금도 반경 안에 있는지 확인
            // true  -> 최근 15초 이내 위치 업데이트 + 반경 60m 이내 → 아직 현장에 있음
            // false -> 위치 없음 or 오래된 위치 or 반경 밖 → 자리를 뜬 것으로 판단
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

            // 등록자는 있고 신청자가 없는 경우
            if (authorInRange && !applicantInRange) {
                meetVerification.markApplicantNoShow();
                notificationPublisher.sendNoShowWarning(matchInfoDto.applicantId(), matchId);

                // 신청자는 있고 등록자가 없는 경우
            } else if (!authorInRange && applicantInRange) {
                meetVerification.markAuthorNoShow();
                notificationPublisher.sendNoShowWarning(postInfoDto.authorId(), matchId);

                // 둘 다 없을 때
            } else {
                meetVerification.markBothNoShow();
                notificationPublisher.sendNoShowWarning(postInfoDto.authorId(), matchId);
                notificationPublisher.sendNoShowWarning(matchInfoDto.applicantId(), matchId);
            }

            // 위치 정보 지우기
            userLocationCleanupService.deleteLocationsByMatchId(meetVerification.getMatchId());

            // 채팅방 읽기전용으로 전환
            chatService.makeReadOnlyChatRoom(postId);
        }
    }

    // 노쇼 확정
    @Override
    @Transactional
    public void judgeNoShowConfirmed() {

        // 노쇼 확정 기준 시각 계산
        // noShowDecidedAt이 이 시각보다 이전인 건 = 24시간이 지난 건
        LocalDateTime deadline = LocalDateTime.now().minusHours(NO_SHOW_CONFIRM_HOURS);

        // NO_SHOW 상태이면서 noShowDecidedAt이 24시간 이전인 건 전체 조회
        List<MeetVerification> noShowList = meetVerificationRepository
                .findAllByStatusInAndNoShowDecidedAtBefore(NO_SHOW_STATUSES, deadline);

        // 처리할 건이 없으면 조기 종료 — 불필요한 외부 서비스 호출 방지
        if (noShowList.isEmpty()) {
            return;
        }

        // 이후 벌크 조회에 사용할 matchId 목록 추출
        List<Long> matchIds = noShowList.stream()
                .map(MeetVerification::getMatchId)
                .toList();

        // Match 정보 벌크 조회 — applicantId 확보용 (N+1 방지)
        Map<Long, MatchInfoDto> matchInfoMap = matchService.getMatchInfos(matchIds);

        // postId 목록 추출 — Post 벌크 조회 준비
        List<Long> postIds = matchInfoMap.values().stream()
                .map(MatchInfoDto::postId)
                .distinct()
                .toList();

        // Post 정보 벌크 조회 — authorId 확보용 (N+1 방지)
        Map<Long, PostInfoDto> postInfoMap = postQueryService.getPostInfos(postIds);

        // // 관리자가 아직 검토 중인 이의제기가 있는 matchId Set 조회
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
            MatchInfoDto matchInfoDto = matchInfoMap.get(matchId);
            if (matchInfoDto == null) continue;

            PostInfoDto postInfoDto = postInfoMap.get(matchInfoDto.postId());
            if (postInfoDto == null) continue;

            VerificationStatus status = meetVerification.getStatus();

            // 노쇼 상태에 따라 Match 도메인에 확정 처리 위임
            if (status == VerificationStatus.BOTH_NO_SHOW) {
                // 양측 모두 노쇼 확정 — 양측 예치금 전부 몰수
                matchService.markBothNoShow(matchId);
                // 양측에게 노쇼 확정 알림 발송
                notificationPublisher.sendNoShowConfirmed(postInfoDto.authorId(), matchId);
                notificationPublisher.sendNoShowConfirmed(matchInfoDto.applicantId(), matchId);

            } else if (status == VerificationStatus.GUEST_NO_SHOW) {
                // 신청자만 노쇼 확정 — 신청자 예치금 몰수 + 등록자 환급
                matchService.markApplicantNoShow(matchId);
                // 노쇼 당사자인 신청자에게만 확정 알림 발송
                notificationPublisher.sendNoShowConfirmed(matchInfoDto.applicantId(), matchId);

            } else if (status == VerificationStatus.HOST_NO_SHOW) {
                // 등록자만 노쇼 확정 — 등록자 예치금 몰수 + 신청자 환급
                matchService.markAuthorNoShow(matchId);
                // 노쇼 당사자인 등록자에게만 확정 알림 발송
                notificationPublisher.sendNoShowConfirmed(postInfoDto.authorId(), matchId);
            }

            // 처리 완료 — 다음 배치에서 중복 실행 방지
            meetVerification.confirmNoShow();
        }
    }

    // Admin 도메인에서 사용할 노쇼 후보군 조회
    @Override
    public Page<MeetVerification> getNoShowCandidates(Pageable pageable) {
        return meetVerificationRepository.findAllByStatusIn(NO_SHOW_STATUSES, pageable);
    }

    // 연장 요청
    @Override
    @Transactional
    public CreateMeetExtensionResponseDto createMeetExtension(Long userId, Long matchId) {

        // MeetVerification 조회
        MeetVerification meetVerification = meetVerificationRepository.findByMatchId(matchId)
                .orElseThrow(() -> new MeetException(ErrorCode.MEET_VERIFICATION_NOT_FOUND));

        // Match, Post 정보 조회
        MatchInfoDto matchInfoDto = matchService.getMatchInfo(matchId);
        PostInfoDto postInfoDto = postQueryService.getPostInfo(matchInfoDto.postId());

        // 당사자 확인
        if (!matchInfoDto.isParticipant(userId, postInfoDto.authorId())) {
            throw new MeetException(ErrorCode.MATCH_NOT_PARTICIPANT);
        }

        // MATCH 상태 확인 (노쇼 판정 이후 or 완료된 매칭엔 연장 불가)
        if (matchInfoDto.status() != MatchStatus.MATCHED) {
            throw new MeetException(ErrorCode.MEET_EXTEND_MATCH_NOT_MATCHED);
        }

        // 연장 요청은 약속시간 5분 전까지만 가능
        if (!LocalDateTime.now().isBefore(postInfoDto.meetAt().minusMinutes(EXTENSION_TIMEOUT_MINUTES))) {
            throw new MeetException(ErrorCode.MEET_EXTEND_BEFORE_MEET_AT);
        }

        // ACCEPTED -> 이미 1회 연장 성공 -> 영영 불가,
        // REJECTED -> 거절됨 -> 영영 불가
        if (meetVerification.isExtended() || meetVerification.getExtensionStatus() == ExtensionStatus.REJECTED) {
            throw new MeetException(ErrorCode.MEET_EXTEND_ALREADY_ACCEPTED);
        }

        // 진행 중인 요청(REQUESTED) 체크 전에 만료 여부를 먼저 확인,
        // 스케줄러가 EXPIRED로 전환하지 않은 타이밍에도 재요청을 허용하기 위함
        if (meetVerification.getExtensionStatus() == ExtensionStatus.REQUESTED) {

            // 5분 타임아웃이 지났으면 -> 즉시 EXPIRED 처리 후 요청 허용
            if (meetVerification.isExtensionExpired()) {
                meetVerification.expireExtension();
            } else {
                // 아직 5분 안 지났으면 진행 중인 요청이 있으므로 차단
                throw new MeetException(ErrorCode.MEET_EXTEND_ALREADY_REQUESTED);
            }
        }

        // 연장 요청 처리
        meetVerification.requestExtension(userId);

        // 상대방에게 연장 요청 알림 발송
        Long opponentId = userId.equals(postInfoDto.authorId()) ? matchInfoDto.applicantId() : postInfoDto.authorId();
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

    // 연장 수락
    @Override
    @Transactional
    public AcceptMeetExtensionResponseDto acceptMeetExtension(Long userId, Long matchId) {

        MeetVerification meetVerification = meetVerificationRepository.findByMatchId(matchId)
                .orElseThrow(() -> new MeetException(ErrorCode.MEET_VERIFICATION_NOT_FOUND));

        MatchInfoDto matchInfoDto = matchService.getMatchInfo(matchId);
        PostInfoDto postInfoDto = postQueryService.getPostInfo(matchInfoDto.postId());

        // 당사자 확인
        if (!matchInfoDto.isParticipant(userId, postInfoDto.authorId())) {
            throw new MeetException(ErrorCode.MATCH_NOT_PARTICIPANT);
        }

        // 만료 여부 확인
        if (meetVerification.getExtensionStatus() == ExtensionStatus.REQUESTED && meetVerification.isExtensionExpired()) {
            // 만료 처리 후 예외던지기
            meetVerification.expireExtension();
            throw new MeetException(ErrorCode.MEET_EXTEND_EXPIRED);
        }

        // 응답 가능한 요청이 있는지 확인
        if (meetVerification.getExtensionStatus() != ExtensionStatus.REQUESTED) {
            throw new MeetException(ErrorCode.MEET_EXTEND_NO_ACTIVE_REQUEST);
        }

        // 본인 요청은 본인이 수락 불가
        if (userId.equals(meetVerification.getExtensionRequesterId())) {
            throw new MeetException(ErrorCode.MEET_EXTEND_SELF_RESPONSE);
        }

        // 수락 처리 -> meetAt + 15분을 extendedMeetAt에 저장
        meetVerification.acceptExtension(postInfoDto.meetAt(), EXTENSION_MINUTES);

        // QR 만료 시각도 15분 연장
        meetVerification.extendQrExpiry(EXTENSION_MINUTES);

        // 연장 요청자에게 수락 알림 발송
        notificationPublisher.sendMeetExtendAccepted(meetVerification.getExtensionRequesterId(), matchId);

        // 수락 시 타임아웃 예약 제거 (더 이상 만료 처리 불필요)
        redisTemplate.opsForZSet().remove(
                MeetRedisZSetKeys.EXTENSION_TIMEOUT,
                String.valueOf(meetVerification.getId())
        );

        return AcceptMeetExtensionResponseDto.of(meetVerification, postInfoDto.meetAt());
    }

    // 연장 거절
    @Override
    @Transactional
    public RejectMeetExtensionResponseDto rejectMeetExtension(Long userId, Long matchId) {

        MeetVerification meetVerification = meetVerificationRepository.findByMatchId(matchId)
                .orElseThrow(() -> new MeetException(ErrorCode.MEET_VERIFICATION_NOT_FOUND));

        MatchInfoDto matchInfoDto = matchService.getMatchInfo(matchId);
        PostInfoDto postInfoDto = postQueryService.getPostInfo(matchInfoDto.postId());

        // 당사자 확인
        if (!matchInfoDto.isParticipant(userId, postInfoDto.authorId())) {
            throw new MeetException(ErrorCode.MATCH_NOT_PARTICIPANT);
        }

        // 만료 여부 확인
        if (meetVerification.getExtensionStatus() == ExtensionStatus.REQUESTED && meetVerification.isExtensionExpired()) {
            meetVerification.expireExtension();
            throw new MeetException(ErrorCode.MEET_EXTEND_EXPIRED);
        }

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

        // 연장 요청자에게 거절 알림 발송
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

        MeetVerification meetVerification = meetVerificationRepository.findByMatchId(matchId)
                .orElseThrow(() -> new MeetException(ErrorCode.MEET_VERIFICATION_NOT_FOUND));

        MatchInfoDto matchInfoDto = matchService.getMatchInfo(matchId);
        PostInfoDto postInfoDto = postQueryService.getPostInfo(matchInfoDto.postId());

        // 당사자 확인
        if (!matchInfoDto.isParticipant(userId, postInfoDto.authorId())) {
            throw new MeetException(ErrorCode.MATCH_NOT_PARTICIPANT);
        }

        // NONE 상태면 아직 요청자 없음 -> 닉네임 null 처리
        String requesterNickname = null;
        if (meetVerification.getExtensionRequesterId() != null) {
            requesterNickname = userService.getUserInfo(meetVerification.getExtensionRequesterId()).nickname();
        }

        return GetMeetExtensionResponseDto.of(meetVerification, requesterNickname, postInfoDto.meetAt(), userId);
    }

    // 요청 시간 만료
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

            // 만료 알림 발송
            notificationPublisher.sendMeetExtendExpired(mv.getExtensionRequesterId(), mv.getMatchId());
        });

    }

    @Override
    public MeetVerification getByMatchId(Long matchId) {
        return meetVerificationRepository.findByMatchId(matchId)
                .orElseThrow(() -> new MeetException(ErrorCode.MEET_VERIFICATION_NOT_FOUND));
    }

    // QR 토큰 발급 — 중복 발급 방지 포함
    // 호출 시점: 양측 GPS 인증 완료 직후
    private void issueQrTokenIfNeeded(MeetVerification meetVerification) {

        // 이미 발급된 토큰이 있으면 스킵 — 멱등성 보장 (여러 번 호출해도 1번만 발급됨)
        if (meetVerification.getQrToken() != null) {
            return;
        }

        // 양측 GPS 인증이 완료되지 않았으면 스킵 (정상적으로는 도달하지 않아야 하는 방어 코드)
        if (meetVerification.getAuthorPlaceVerifiedAt() == null || meetVerification.getApplicantPlaceVerifiedAt() == null) {
            return;
        }

        // QR 만료 시각 = 양측 중 더 나중에 인증한 시각 + 30분
        LocalDateTime placeVerifiedCompletedAt = meetVerification.getAuthorPlaceVerifiedAt()
                .isAfter(meetVerification.getApplicantPlaceVerifiedAt())
                ? meetVerification.getAuthorPlaceVerifiedAt() : meetVerification.getApplicantPlaceVerifiedAt();

        LocalDateTime expiresAt = placeVerifiedCompletedAt.plusMinutes(QR_TOKEN_VALIDITY_MINUTES);
        String qrToken = "hp_qr_" + UUID.randomUUID().toString().replace("-", "");

        meetVerification.issueQrToken(qrToken, expiresAt);

    }
}


