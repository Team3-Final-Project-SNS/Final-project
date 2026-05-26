package com.example.team3final.domain.meet.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.VerificationException;
import com.example.team3final.domain.chat.service.ChatService;
import com.example.team3final.domain.location.service.UserLocationService;
import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.service.MatchService;
import com.example.team3final.domain.meet.dto.request.PlaceVerificationRequestDto;
import com.example.team3final.domain.meet.dto.request.QrScanRequestDto;
import com.example.team3final.domain.meet.dto.response.*;
import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.enums.ExtensionStatus;
import com.example.team3final.domain.meet.enums.VerificationStatus;
import com.example.team3final.domain.meet.repository.MeetVerificationRepository;
import com.example.team3final.domain.post.dto.response.PostInfoDto;
import com.example.team3final.domain.post.service.PostService;
import com.example.team3final.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
    private final UserService userService;

    // GPS 오차범위까지 고려한 인증 반경
    private static final double PLACE_VERIFICATION_RADIUS_METERS = 60.0;
    // 지구 반지름
    private static final int EARTH_RADIUS_METERS = 6371000;
    // QR 토큰 TTL - 장소 인증 완료 시점 + 30분
    private static final long QR_TOKEN_VALIDITY_MINUTES = 30;
    // 장소 인증 가능 시간 : 만남 시간 15분전 ~ 1시간
    private static final long VERIFICATION_BEFORE_MINUTES = 15;
    private static final long VERIFICATION_AFTER_MINUTES = 60;
    // 노쇼 판정 기준 : GPS -> meetAt + 30분
    private static final long NO_SHOW_JUDGE_MINUTES = 30;
    // 연장 요청 타임아웃 : 요청 시각 + 5분
    private static final long EXTENSION_TIMEOUT_MINUTES = 5;
    // 연장 시간
    private static final long EXTENSION_MINUTES = 15;

    // GPS 장소 인증
    @Override
    @Transactional
    public PlaceVerificationResponseDto createPlaceVerification(
            Long userId,
            Long matchId,
            PlaceVerificationRequestDto requestDto) {

        // matchId로 MeetVerification 조회
        MeetVerification meetVerification = meetVerificationRepository.findByMatchId(matchId)
                .orElseThrow(() -> new VerificationException(ErrorCode.MEET_VERIFICATION_NOT_FOUND));

        // MatchInfoDto 조회
        MatchInfoDto matchInfo = matchService.getMatchInfo(matchId);

        // PostInfoDto 조회
        // match -> postId -> post 순서대로 (Match에는 authorId 없음)
        PostInfoDto postInfo = postQueryService.getPostInfo(matchInfo.postId());

        // 매칭 당사자가 맞는지 검증 (등록자 or 신청자인지)
        if (!matchInfo.isParticipant(userId, postInfo.authorId())) {
            throw new VerificationException(ErrorCode.MATCH_NOT_PARTICIPANT);
        }

        // meetAt 기준 15분 전 ~ 1시간 범위 체크
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime verificationStartTime = postInfo.meetAt().minusMinutes(VERIFICATION_BEFORE_MINUTES);
        LocalDateTime verificationEndTime = postInfo.meetAt().plusMinutes(VERIFICATION_AFTER_MINUTES);

        if (now.isBefore(verificationStartTime) || now.isAfter(verificationEndTime)) {
            throw new VerificationException(ErrorCode.GPS_NOT_VERIFICATION_TIME);
        }

        // 이미 본인이 인증 완료했는지 체크
        // userId 기반으로 등록저/신청자 구분하여 각각 체크
        boolean isAuthor = userId.equals(postInfo.authorId());
        if (isAuthor && meetVerification.isAuthorPlaceVerified()) {
            throw new VerificationException(ErrorCode.GPS_ALREADY_VERIFIED);
        }
        if (!isAuthor && meetVerification.isApplicantPlaceVerified()) {
            throw new VerificationException(ErrorCode.GPS_ALREADY_VERIFIED);
        }

        // placeLat, placeLng를 Post에서 조회
        BigDecimal placeLat = postInfo.placeLat();
        BigDecimal placeLng = postInfo.placeLng();

        // BigDecimal → double 변환: Math 삼각함수가 double만 지원하므로 계산 직전에만 변환
        double distanceMeters = calculateDistance(
                requestDto.getCurrentLat().doubleValue(), requestDto.getCurrentLng().doubleValue(),
                placeLat.doubleValue(), placeLng.doubleValue()
        );

        // 반경 60m(오차 범위 포함) 벗어났는지 체크
        if (distanceMeters > PLACE_VERIFICATION_RADIUS_METERS) {
            throw new VerificationException(ErrorCode.GPS_OUT_OF_RANGE);
        }

        // userId 기반으로 등록자/신청자 구분하여 각각 인증 처리
        if (isAuthor) {
            meetVerification.verifyAuthorPlace();
        } else {
            meetVerification.verifyApplicantPlace();
        }

        boolean bothVerified = meetVerification.getStatus() == VerificationStatus.VERIFIED;

        return PlaceVerificationResponseDto.of(meetVerification, distanceMeters, bothVerified);
    }

    @Override
    @Transactional
    public QrResponseDto getMeetQr(Long matchId, Long userId) {
        // matchId 조회
        MeetVerification meetVerification = meetVerificationRepository.findByMatchId(matchId)
                .orElseThrow(() -> new VerificationException(ErrorCode.MEET_VERIFICATION_NOT_FOUND));

        // MatchInfoDto -> PostInfoDto 순으로 타서 authorId 획득
        MatchInfoDto matchInfo = matchService.getMatchInfo(matchId);
        PostInfoDto postInfo = postQueryService.getPostInfo(matchInfo.postId());

        // 등록자인지 확인 (QR 발급은 등록자만 가능!)
        if (!userId.equals(postInfo.authorId())) {
            throw new VerificationException(ErrorCode.QR_NOT_AUTHOR);
        }

        // 장소 인증 완료된 상태인지 체크
        if (meetVerification.getStatus() != VerificationStatus.VERIFIED) {
            throw new VerificationException(ErrorCode.QR_PLACE_VERIFICATION_REQUIRED);
        }

        // QR 토큰 이미 발급했는지 확인
        if (meetVerification.getQrToken() != null) {
            // 이미 만료됐으면 예외 던지기
            if (meetVerification.isQrExpired()) {
                throw new VerificationException(ErrorCode.QR_EXPIRED);
            }

            return QrResponseDto.of(matchId, meetVerification);
        }

        // QR 토큰 신규 발급
        // "hp_qr_" -> API 명세서 상의 접두사, qr 토큰 식별 용도
        String qrToken = "hp_qr_" + UUID.randomUUID().toString().replace("-", "");

        // 양측 장소 인증 완료 시점 + 30분
        // 둘 중 누가 먼저 올지 모르기 때문에, 둘 다 null이 아님이 보장된 상태일 때
        LocalDateTime lastVerifiedAt = meetVerification.getAuthorPlaceVerifiedAt()
                .isAfter(meetVerification.getApplicantPlaceVerifiedAt())
                ? meetVerification.getAuthorPlaceVerifiedAt() : meetVerification.getApplicantPlaceVerifiedAt();

        LocalDateTime expiresAt = lastVerifiedAt.plusMinutes(QR_TOKEN_VALIDITY_MINUTES);

        // 엔티티에 QR 토큰 저장
        meetVerification.issueQrToken(qrToken, expiresAt);

        return QrResponseDto.of(matchId, meetVerification);
    }

    @Override
    @Transactional
    public QrScanResponseDto createQrScan(Long userId, Long matchId, QrScanRequestDto requestDto) {

        // matchId 조회
        MeetVerification meetVerification = meetVerificationRepository.findByMatchId(matchId)
                .orElseThrow(() -> new VerificationException(ErrorCode.MEET_VERIFICATION_NOT_FOUND));

        // MatchInfoDto 조회로 신청자 검증
        MatchInfoDto matchInfo = matchService.getMatchInfo(matchId);

        // 신청자인지 확인 (QR 스캔은 신청자만 가능!)
        if (!matchInfo.isApplicant(userId)) {
            throw new VerificationException(ErrorCode.SCAN_NOT_APPLICANT);
        }

        // DONE 상태 재스캔 차단
        if (meetVerification.getStatus() == VerificationStatus.DONE) {
            throw new VerificationException(ErrorCode.GPS_ALREADY_VERIFIED);
        }

        // 장소 인증 완료 상태인지 체크
        if (meetVerification.getStatus() != VerificationStatus.VERIFIED) {
            throw new VerificationException(ErrorCode.QR_PLACE_VERIFICATION_REQUIRED);
        }

        // QR 토큰 만료 여부 체크
        if (meetVerification.isQrExpired()) {
            throw new VerificationException(ErrorCode.QR_EXPIRED);
        }

        // QR 토큰 일치 여부 검증
        if (!requestDto.getQrToken().equals(meetVerification.getQrToken())) {
            throw new VerificationException(ErrorCode.SCAN_INVALID_QR_TOKEN);
        }

        // 만남 인증 완료 처리
        meetVerification.meetVerifiedDone();

        userLocationService.deleteLocationsByMatchId(matchId);

        // 만남 인증 완료 되는 순간 채팅방 비활성화 실행
        chatService.scheduleChatRoomDeactivation(matchId);

        // Match 상태 COMPLETED로 변경
        matchService.completeMatch(matchId);

        return QrScanResponseDto.of(matchId, meetVerification, MatchStatus.COMPLETED, 0);
    }

    // QR 인증 상태 조회
    @Override
    public MeetVerificationResponseDto getMeetVerification(Long userId, Long matchId) {

        MeetVerification meetVerification = meetVerificationRepository.findByMatchId(matchId)
                .orElseThrow(() -> new VerificationException(ErrorCode.MEET_VERIFICATION_NOT_FOUND));

        // MatchInfoDto → PostInfoDto 순으로 타서 authorId 획득
        MatchInfoDto matchInfo = matchService.getMatchInfo(matchId);
        PostInfoDto postInfo = postQueryService.getPostInfo(matchInfo.postId());

        // 매칭 당사자 검증
        if (!matchInfo.isParticipant(userId, postInfo.authorId())) {
            throw new VerificationException(ErrorCode.MATCH_NOT_PARTICIPANT);
        }

        return MeetVerificationResponseDto.of(matchId, meetVerification);
    }

    @Override
    @Transactional
    public void createPendingVerification(Long matchId) {
        // 매칭 생성 시점에 PENDING 상태로 MeetVerification 레코드 초기화
        meetVerificationRepository.save(MeetVerification.createPending(matchId));
    }

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
        List<Long> matchId = pendingList.stream()
                .map(MeetVerification::getMatchId)
                .toList();

        // Match 도메인에 한 번에 조회 요청 (벌크 조회)
        Map<Long, MatchInfoDto> matchInfoDtoMap = matchService.getMatchInfos(matchId);

        // 위에서 받은 MatchInfo들에서 postId만 추출
        List<Long> postId = matchInfoDtoMap.values()
                .stream()
                .map(MatchInfoDto::postId)
                .toList();

        // Post 도메인에 한 번에 조회 요청
        Map<Long, PostInfoDto> postInfoDtoMap = postQueryService.getPostInfos(postId);

        for (MeetVerification meetVerification : pendingList) {
            // 만약, 누락 된 matchId가 Map에 없을 수 있으므로, 이러한 경우 null 반환
            MatchInfoDto matchInfoDto = matchInfoDtoMap.get(meetVerification.getMatchId());
            if (matchInfoDto == null) {
                // 데이터 정합성이 깨졌을 때를 대비한 방어 -> 해당 건만 스킵
                continue;
            }

            PostInfoDto postInfoDto = postInfoDtoMap.get(meetVerification.getMatchId());
            if (postInfoDto == null) {
                continue;
            }

            // meetAt + 30분이 아직 안 지났으면 판정 시점 전이므로, 다음건으로
            LocalDateTime deadline = postInfoDto.meetAt().plusMinutes(NO_SHOW_JUDGE_MINUTES);
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
                matchService.markBothNoShow(meetVerification.getMatchId());
                userLocationService.deleteLocationsByMatchId(meetVerification.getMatchId());
                chatService.deactivateChatRoom(meetVerification.getMatchId());

            } else if (authorVerified && !applicantVerified) {
                // 신청자가 노쇼 -> GUEST_NO_SHOW
                meetVerification.markApplicantNoShow();
                matchService.markApplicantNoShow(meetVerification.getMatchId());
                userLocationService.deleteLocationsByMatchId(meetVerification.getMatchId());
                chatService.deactivateChatRoom(meetVerification.getMatchId());
            } else if (!authorVerified) {
                // 등록자 노쇼 -> HOST_NO_SHOW
                meetVerification.markAuthorNoShow();
                matchService.markAuthorNoShow(meetVerification.getMatchId());
                userLocationService.deleteLocationsByMatchId(meetVerification.getMatchId());
                chatService.deactivateChatRoom(meetVerification.getMatchId());
            }
        }
    }

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

        // QR단계에서 만료된 건 -> 신청자가 스캔을 안 한 케이스 -> 일괄 신청자 노쇼
        for (MeetVerification meetVerification : expiresList) {
            // 자신의 상태를 신청자 노쇼로 변경
            meetVerification.markApplicantNoShow();

            // Match 도메인에 신청자 노쇼로 알려주기
            matchService.markApplicantNoShow(meetVerification.getMatchId());

            // 위치 정보 지우기
            userLocationService.deleteLocationsByMatchId(meetVerification.getMatchId());

            // 채팅방 비활성화
            chatService.deactivateChatRoom(meetVerification.getMatchId());
        }
    }

    // Admin 도메인에서 사용할 노쇼 후보군 조회
    @Override
    public Page<MeetVerification> getNoShowCandidates(Pageable pageable) {
        return meetVerificationRepository.findAllByStatusIn(NO_SHOW_STATUSES, pageable);
    }

    // 연장 요청
    @Override
    public CreateMeetExtensionResponseDto createMeetExtension(Long userId, Long matchId) {

        // MeetVerification 조회
        MeetVerification meetVerification = meetVerificationRepository.findByMatchId(matchId)
                .orElseThrow(() -> new VerificationException(ErrorCode.MEET_VERIFICATION_NOT_FOUND));

        // Match, Post 정보 조회
        MatchInfoDto matchInfoDto = matchService.getMatchInfo(matchId);
        PostInfoDto postInfoDto = postQueryService.getPostInfo(matchInfoDto.postId());

        // 당사자 확인
        if (!matchInfoDto.isParticipant(userId, postInfoDto.postId())) {
            throw new VerificationException(ErrorCode.MATCH_NOT_PARTICIPANT);
        }

        // MATCH 상태 확인 (노쇼 판정 이후 or 완료된 매칭엔 연장 불가)
        if (matchInfoDto.status() != MatchStatus.MATCHED) {
            throw new VerificationException(ErrorCode.MEET_EXTEND_MATCH_NOT_MATCHED);
        }

        // 연장 요청은 약속시간 5분 전까지만 가능
        if (!LocalDateTime.now().isBefore(postInfoDto.meetAt().minusMinutes(5))) {
            throw new VerificationException(ErrorCode.MEET_EXTEND_BEFORE_MEET_AT);
        }

        // 이미 연장 성공한 매칭인지 확인 (1회 한정)
        if (meetVerification.isExtended()) {
            throw new VerificationException(ErrorCode.MEET_EXTEND_ALREADY_ACCEPTED);
        }

        // 이미 진행 중인 연장 요청이 있는지 확인
        if (meetVerification.getExtensionStatus() == ExtensionStatus.REQUESTED) {
            throw new VerificationException(ErrorCode.MEET_EXTEND_ALREADY_REQUESTED);
        }

        // 연장 요청 처리
        meetVerification.requestExtension(userId);

        // 요청자 닉네임 조회
        String requesterNickname = userService.getUserInfo(userId).nickname();

        return CreateMeetExtensionResponseDto.of(meetVerification, requesterNickname, postInfoDto.meetAt());
    }

    // 연장 수락
    @Override
    @Transactional
    public AcceptMeetExtensionResponseDto acceptMeetExtension(Long userId, Long matchId) {

        MeetVerification meetVerification = meetVerificationRepository.findByMatchId(matchId)
                .orElseThrow(() -> new VerificationException(ErrorCode.MEET_VERIFICATION_NOT_FOUND));

        MatchInfoDto matchInfoDto = matchService.getMatchInfo(matchId);
        PostInfoDto postInfoDto = postQueryService.getPostInfo(matchInfoDto.postId());

        // 당사자 확인
        if (!matchInfoDto.isParticipant(userId, postInfoDto.authorId())) {
            throw new VerificationException(ErrorCode.MATCH_NOT_PARTICIPANT);
        }

        // 응답 가능한 요청이 있는지 확인
        if (meetVerification.getExtensionStatus() != ExtensionStatus.REQUESTED) {
            throw new VerificationException(ErrorCode.MEET_EXTEND_NO_ACTIVE_REQUEST);
        }

        // 만료 여부 확인
        if (meetVerification.isExtensionExpired()) {
            // 만료 처리 후 예외던지기
            meetVerification.expireExtension();
            throw new VerificationException(ErrorCode.MEET_EXTEND_EXPIRED);
        }

        // 본인 요청은 본인이 수락 불가
        if (userId.equals(meetVerification.getExtensionRequesterId())) {
            throw new VerificationException(ErrorCode.MEET_EXTEND_SELF_RESPONSE);
        }

        // 수락 처리 -> meetAt + 15분을 extendedMeetAt에 저장
        meetVerification.acceptExtension(postInfoDto.meetAt());

        // QR 만료 시각도 15분 연장
        meetVerification.extendQrExpiry();

        return AcceptMeetExtensionResponseDto.of(meetVerification, postInfoDto.meetAt());
    }

    // 연장 거절
    @Override
    @Transactional
    public RejectMeetExtensionResponseDto rejectMeetExtension(Long userId, Long matchId) {

        MeetVerification meetVerification = meetVerificationRepository.findByMatchId(matchId)
                .orElseThrow(() -> new VerificationException(ErrorCode.MEET_VERIFICATION_NOT_FOUND));

        MatchInfoDto matchInfoDto = matchService.getMatchInfo(matchId);
        PostInfoDto postInfoDto = postQueryService.getPostInfo(matchInfoDto.postId());

        // 당사자 확인
        if (!matchInfoDto.isParticipant(userId, postInfoDto.authorId())) {
            throw new VerificationException(ErrorCode.MATCH_NOT_PARTICIPANT);
        }

        // 응답 가능한 요청 있는지 확인
        if (meetVerification.getExtensionStatus() != ExtensionStatus.REQUESTED) {
            throw new VerificationException(ErrorCode.MEET_EXTEND_NO_ACTIVE_REQUEST);
        }

        // 만료 여부 확인
        if (meetVerification.isExtensionExpired()) {
            meetVerification.expireExtension();
            throw new VerificationException(ErrorCode.MEET_EXTEND_EXPIRED);
        }

        // 본인 요청은 본인이 거절 불가
        if (userId.equals(meetVerification.getExtensionRequesterId())) {
            throw new VerificationException(ErrorCode.MEET_EXTEND_SELF_RESPONSE);
        }

        // 거절 처리
        meetVerification.rejectExtension();

        return RejectMeetExtensionResponseDto.from(meetVerification);

    }

    // 연장 상태 조회
    @Override
    public GetMeetExtensionResponseDto getMeetExtension(Long userId, Long matchId) {

        MeetVerification meetVerification = meetVerificationRepository.findByMatchId(matchId)
                .orElseThrow(() -> new VerificationException(ErrorCode.MEET_VERIFICATION_NOT_FOUND));

        MatchInfoDto matchInfoDto = matchService.getMatchInfo(matchId);
        PostInfoDto postInfoDto = postQueryService.getPostInfo(matchInfoDto.postId());

        // 당사자 확인
        if (!matchInfoDto.isParticipant(userId, postInfoDto.authorId())) {
            throw new VerificationException(ErrorCode.MATCH_NOT_PARTICIPANT);
        }

        // NONE 상태면 아직 요청자 없음 -> 닉네임 null 처리
        String requesterNickname = null;
        if (meetVerification.getExtensionRequesterId() != null) {
            requesterNickname = userService.getUserInfo(meetVerification.getExtensionRequesterId()).nickname();
        }

        return GetMeetExtensionResponseDto.of(meetVerification, requesterNickname, postInfoDto.meetAt(), userId);
    }

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
        expiredList.forEach(MeetVerification::expireExtension);

    }

    private static final List<VerificationStatus> NO_SHOW_STATUSES = List.of(
            VerificationStatus.HOST_NO_SHOW,
            VerificationStatus.GUEST_NO_SHOW,
            VerificationStatus.BOTH_NO_SHOW
    );

    // Haversine 공식으로 두 GPS 좌표 사이 거리 계산
    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double n = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double m = 2 * Math.atan2(Math.sqrt(n), Math.sqrt(1 - n));

        return EARTH_RADIUS_METERS * m;
    }
}


