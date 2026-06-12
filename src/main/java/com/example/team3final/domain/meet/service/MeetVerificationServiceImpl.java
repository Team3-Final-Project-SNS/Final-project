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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
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

//    // 노쇼 확정까지 이의제기 가능 시간: 24시간
//    private static final long NO_SHOW_CONFIRM_HOURS = 24;

    // ===== 변경 (테스트용 — 배포 전 24시간으로 원복 필요) =====
    private static final long NO_SHOW_CONFIRM_MINUTES = 10;

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

        // 양측 장소 인증 완료 시 Post 기준 공통 QR 토큰을 발급하거나 기존 토큰을 재사용한다.
        if (bothVerified) {
            issueQrTokenIfNeeded(meetVerification, matchInfo.postId());
        }

        // 14. 장소 인증 완료 알림
        // 장소 인증 완료 알림.
        // 정책:
        // - 1:1: 인증한 사람을 제외한 상대방에게 발송
        // - 그룹: 인증한 사람을 제외한 모임 참여자 전원에게 발송
        notifyPlaceVerifiedToParticipants(
                userId,
                matchInfo.postId(),
                matchId,
                postInfo.authorId()
        );

        return PlaceVerificationResponseDto.of(meetVerification, distanceMeters, bothVerified);
    }

    @Override
    @Transactional
    public QrResponseDto getMeetQrByPost(Long userId, Long postId) {

        // Post 정보를 조회해서 요청자가 등록자인지 확인
        PostInfoDto postInfoDto = postQueryService.getPostInfo(postId);

        // QR은 등록자만 화면에 띄울 수 있으므로, 작성자 검증
        if (!userId.equals(postInfoDto.authorId())) {
            throw new MeetException(ErrorCode.QR_NOT_AUTHOR);
        }

        // 같은 Post에 속한 모든 Match ID를 조회
        List<Long> siblingMatchIds = matchService.getMatchIdsByPostId(postId);

        // 매칭이 하나도 없다면 아직 QR을 발급할 수 없는 상태
        if (siblingMatchIds.isEmpty()) {
            throw new MeetException(ErrorCode.QR_PLACE_VERIFICATION_REQUIRED);
        }

        // 같은 Post에 속한 MeetVerification을 벌크 조회
        List<MeetVerification> siblingMvList = meetVerificationRepository.findAllByMatchIdIn(siblingMatchIds);

        // 등록자와 신청자 양측 장소 인증이 끝난 MeetVerification이 하나라도 있는지 확인
        MeetVerification verified = siblingMvList.stream()
                .filter(mv -> mv.getStatus() == VerificationStatus.VERIFIED)
                .findFirst()
                .orElseThrow( () -> new MeetException(ErrorCode.QR_PLACE_VERIFICATION_REQUIRED));

        // 같은 Post에 이미 발급된 공통 QR 토큰이 있는지 확인
        Optional<MeetVerification> tokenOwnerOpt = findPostQrTokenOwner(siblingMvList);

        MeetVerification tokenOwner;

        if (tokenOwnerOpt.isPresent()) {
            // 이미 발급된 공통 QR 토큰이 있다면 그 토큰을 재사용
            tokenOwner = tokenOwnerOpt.get();
        } else {
            // 아직 QR 토큰이 없다면 VERIFIED 상태의 MeetVerification에 최초 발급
            issueQrTokenIfNeeded(verified, postId);

            // issueQrTokenIfNeeded()가 verified에 QR 토큰을 발급했으므로,
            // 이 verified가 현재 Post의 QR token owner가 됨
            tokenOwner = verified;
        }

        // 공통 QR 토큰의 만료 여부를 확인
        if (tokenOwner.isQrExpired()) {
            throw new MeetException(ErrorCode.QR_EXPIRED);
        }

        // Post 기준 공통 QR 응답을 반환
        return QrResponseDto.of(
                postId,
                tokenOwner.getQrToken(),
                tokenOwner.getQrExpiresAt()
        );
    }

    // QR 스캔 (신청자 전용)
    @Override
    @Transactional
    public QrScanResponseDto createQrScan(Long userId, Long matchId, QrScanRequestDto requestDto) {

        // matchId로 신청자 본인의 MeetVerification을 조회
        MeetVerification meetVerification = meetVerificationRepository.findByMatchId(matchId)
                .orElseThrow(() -> new MeetException(ErrorCode.MEET_VERIFICATION_NOT_FOUND));

        // 신청자 검증과 postId 확인을 위해 MatchInfo를 조회
        MatchInfoDto matchInfo = matchService.getMatchInfo(matchId);

        // QR 스캔은 해당 Match의 신청자만 수행할 수 있음
        if (!matchInfo.isApplicant(userId)) {
            throw new MeetException(ErrorCode.SCAN_NOT_APPLICANT);
        }

        // 이미 DONE 상태라면 중복 스캔이므로 차단
        if (meetVerification.getStatus() == VerificationStatus.DONE) {
            throw new MeetException(ErrorCode.GPS_ALREADY_VERIFIED);
        }

        // 신청자 본인의 Match에서 양측 장소 인증이 완료되어야 QR 스캔이 가능
        if (meetVerification.getStatus() != VerificationStatus.VERIFIED) {
            throw new MeetException(ErrorCode.QR_PLACE_VERIFICATION_REQUIRED);
        }

        // 같은 Post에 발급된 공통 QR 토큰을 가진 MeetVerification을 조회
        MeetVerification tokenOwner = getPostQrTokenOwner(matchInfo.postId())
                .orElseThrow(() -> new MeetException(ErrorCode.QR_PLACE_VERIFICATION_REQUIRED));

        // QR 만료 여부는 신청자 본인의 MV가 아니라 공통 토큰을 가진 MV 기준으로 판단
        if (tokenOwner.isQrExpired()) {
            throw new MeetException(ErrorCode.QR_EXPIRED);
        }

        // 요청으로 들어온 QR 토큰이 Post 공통 QR 토큰과 일치하는지 검증
        if (!requestDto.getQrToken().equals(tokenOwner.getQrToken())) {
            throw new MeetException(ErrorCode.SCAN_INVALID_QR_TOKEN);
        }

        // 응답에 환급 포인트를 포함해야 하므로 Match 완료 처리 전에 신청자 예치금을 조회
        Match match = matchService.getMatchById(matchId);

        // 신청자에게 환급될 포인트 금액을 응답용으로 보관
        int refundedPoint = match.getApplicantDeposit();

        // 신청자 본인의 MeetVerification만 DONE 상태로 전환
        meetVerification.meetVerifiedDone();

        // 만남 인증이 끝난 Match의 위치 데이터는 개인정보 최소 수집 원칙에 따라 삭제
        userLocationCleanupService.deleteLocationsByMatchId(matchId);

        // Match 단건 완료와 신청자 예치금 환급은 Match 도메인에 위임
        boolean isLastScan = matchService.completeSingleMatch(matchId);

        // 마지막 신청자의 스캔이라면 Post 완료와 등록자 책임비 환급을 Match 도메인에 위임
        if (isLastScan) {
            matchService.completePostIfAllMatchesCompleted(matchInfo.postId());

            // 모든 신청자의 인증이 끝난 뒤 채팅방 비활성화를 예약
            chatService.scheduleChatRoomDeactivation(matchInfo.postId());
        }

        // QR 스캔 완료 응답을 반환
        return QrScanResponseDto.of(
                matchId,
                meetVerification,
                MatchStatus.COMPLETED,
                refundedPoint
        );
    }

    // 인증 상태 조회
    @Override
    public MeetVerificationResponseDto getMeetVerification(Long userId, Long matchId) {

        // MeetVerification + MatchInfo + PostInfo 한 번에 조회
        MeetContext ctx = loadMeetContext(matchId);

        // 매칭 당사자 검증 (등록자인지 신청자인지)
        validateParticipant(userId, ctx.matchInfo(), ctx.postInfo());

        // 등록자 닉네임 조회
        String authorNickname = userService.getUserInfo(ctx.postInfo.authorId()).nickname();

        // postId 기준으로 모든 형제 matchId 조회
        List<Long> siblingMatchIds = matchService.getMatchIdsByPostId(ctx.matchInfo.postId());

        // 형제 matchId → MeetVerification 목록 조회 (N+1 방지용 벌크 조회)
        List<MeetVerification> siblingMvList = meetVerificationRepository.findAllByMatchIdIn(siblingMatchIds);

        // 형제 matchId → MatchInfoDto 벌크 조회 (N+1 방지용 벌크 조회)
        Map<Long, MatchInfoDto> siblingMatchInfoMap = matchService.getMatchInfos(siblingMatchIds);

        // 신청자 userId 목록 추출
        List<Long> applicantIds = siblingMatchInfoMap.values().stream()
                .map(MatchInfoDto::applicantId)
                .toList();

        // 신청자 닉네임 벌크 조회
        Map<Long, String> nicknameMap = userService.getUserNicknameMap(applicantIds);

        // matchId -> ParticipantInfo 맵 조립
        Map<Long, MeetVerificationResponseDto.ParticipantInfo> applicantInfoMap =
                siblingMatchInfoMap.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey,
                                e -> new MeetVerificationResponseDto.ParticipantInfo(
                                        e.getValue().applicantId(),
                                        nicknameMap.getOrDefault(e.getValue().applicantId(), "알 수 없음")
                                )));

        return MeetVerificationResponseDto.of(
                matchId,
                ctx.meetVerification(),
                authorNickname,
                siblingMvList,
                applicantInfoMap
        );
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
        Map<Long, MatchInfoDto> matchInfoDtoMap = matchService.getMatchInfos(matchIds);

        for (MeetVerification meetVerification : expiresList) {

            Long matchId = meetVerification.getMatchId();

            // 현재 MeetVerification에 대응되는 Match 정보 조회
            MatchInfoDto matchInfoDto = matchInfoDtoMap.get(matchId);

            // 데이터 정합성 방어
            // Match 정보가 없으면 해당 건은 스킵
            if (matchInfoDto == null) {
                continue;
            }

            // Post 정보 조회
            // 장소 좌표, 등록자 ID를 얻기 위해 필요
            PostInfoDto postInfoDto = postQueryService.getPostInfo(matchInfoDto.postId());

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
                    NO_SHOW_RADIUS_METERS,
                    LOCATION_FRESHNESS_SECONDS
            );

            // 신청자가 QR 만료 시점에 아직 약속 장소 반경 안에 있는지 확인
            boolean applicantInRange = userLocationService.isFreshLocationWithinRadius(
                    matchId,
                    matchInfoDto.applicantId(),
                    postInfoDto.placeLat(),
                    postInfoDto.placeLng(),
                    NO_SHOW_RADIUS_METERS,
                    LOCATION_FRESHNESS_SECONDS
            );

            // 등록자가 현재 반경 안에 있는 경우
            if (authorInRange) {

                if (applicantInRange) {

                    // 등록자와 신청자가 둘 다 반경 안에 있음
                    // 정책
                    // QR 만료 시각까지 둘 다 자리에 있었는데 QR 인증이 이루어지지 않음
                    // → 어느 한쪽 노쇼로 보기 어려움
                    // → 귀책 없음으로 해당 Match 취소 처리
                    matchService.cancelMatchBySystem(matchId);

                } else {

                    // 등록자는 반경 안에 있고 신청자는 반경 밖에 있음
                    // → 신청자 노쇼 예정
                    meetVerification.markApplicantNoShow();

                    // GUEST 노쇼는 그룹 채팅방 전체를 잠그지 않고
                    // 해당 신청자만 채팅 제한 처리
                    chatService.markGuestNoShow(
                            matchInfoDto.postId(),
                            matchInfoDto.applicantId()
                    );

                    // 노쇼 예정 알림 중복 발송 방지
                    if (!meetVerification.isNoShowWarningSent()) {
                        notificationPublisher.sendNoShowWarning(
                                matchInfoDto.applicantId(),
                                matchId
                        );
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
                    chatService.makeReadOnlyChatRoom(matchInfoDto.postId());

                    // 노쇼 예정 알림 중복 발송 방지
                    if (!meetVerification.isNoShowWarningSent()) {
                        notificationPublisher.sendNoShowWarning(
                                postInfoDto.authorId(),
                                matchId
                        );
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
                            chatService.makeReadOnlyChatRoom(matchInfoDto.postId());

                            // 노쇼 예정 알림 중복 발송 방지
                            if (!meetVerification.isNoShowWarningSent()) {
                                notificationPublisher.sendNoShowWarning(
                                        postInfoDto.authorId(),
                                        matchId
                                );
                                meetVerification.markNoShowWarningSent();
                            }

                        } else if (firstLeftUserId.equals(matchInfoDto.applicantId())) {

                            // 신청자가 먼저 벗어났으므로 신청자 노쇼 예정
                            meetVerification.markApplicantNoShow();

                            // GUEST 노쇼는 해당 신청자만 채팅 제한
                            chatService.markGuestNoShow(
                                    matchInfoDto.postId(),
                                    matchInfoDto.applicantId()
                            );

                            // 노쇼 예정 알림 중복 발송 방지
                            if (!meetVerification.isNoShowWarningSent()) {
                                notificationPublisher.sendNoShowWarning(
                                        matchInfoDto.applicantId(),
                                        matchId
                                );
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
                        chatService.makeReadOnlyChatRoom(matchInfoDto.postId());

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

    // 노쇼 확정 — 스케줄러가 주기적으로 호출
    // 노쇼 예정 상태에서 24시간이 지나면 최종 확정 처리
    @Override
    @Transactional
    public void judgeNoShowConfirmed() {

//        LocalDateTime deadline =
//                LocalDateTime.now().minusHours(NO_SHOW_CONFIRM_HOURS);

        // ===== 변경 (테스트용 — 배포 전 원복 필요) =====
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(NO_SHOW_CONFIRM_MINUTES);

        List<MeetVerification> noShowList =
                meetVerificationRepository
                        .findAllByStatusInAndNoShowDecidedAtBefore(
                                NO_SHOW_STATUSES,
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

        // ===== Kafka 발행 대상 리스트 — DB 커밋 후 발행하기 위해 모아둠 =====
        List<NoShowConfirmedNotificationTarget> notificationTargets = new ArrayList<>();

        // ===== afterCommit()에서 Kafka 발행 + afterCompletion()에서 COMMIT/ROLLBACK 로그 =====
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {
                        // DB 커밋 성공 후에만 Kafka 알림 발행
                        for (NoShowConfirmedNotificationTarget target : notificationTargets) {
                            notificationPublisher.sendNoShowConfirmed(
                                    target.userId(),
                                    target.matchId()
                            );
                        }
                        log.info("[노쇼확정] 알림 발행 완료 - targets={}", notificationTargets);
                    }

                    @Override
                    public void afterCompletion(int status) {
                        if (status == TransactionSynchronization.STATUS_COMMITTED) {
                            log.info(
                                    "[노쇼확정] 트랜잭션 COMMIT - matchIds={}",
                                    matchIds
                            );
                        } else {
                            log.error(
                                    "[노쇼확정] 트랜잭션 ROLLBACK - matchIds={}",
                                    matchIds
                            );
                        }
                    }
                }
        );

        BulkMatchContext bulk = loadBulkMatchContext(matchIds);

        Set<Long> activeDisputeMatchIds =
                disputeQueryService.getMatchIdsWithActiveDispute(matchIds);

        for (MeetVerification meetVerification : noShowList) {

            Long matchId = meetVerification.getMatchId();

            try {
                log.info(
                        "[노쇼확정] 처리 시작 - matchId={}, status={}, sent={}",
                        matchId,
                        meetVerification.getStatus(),
                        meetVerification.isNoShowConfirmedSent()
                );

                if (activeDisputeMatchIds.contains(matchId)) {
                    log.info(
                            "[노쇼확정] 이의제기 검토 중 스킵 - matchId={}",
                            matchId
                    );
                    continue;
                }

                MatchInfoDto matchInfoDto =
                        bulk.matchInfoMap().get(matchId);

                if (matchInfoDto == null) {
                    log.warn(
                            "[노쇼확정] Match 정보 없음 - matchId={}",
                            matchId
                    );
                    continue;
                }

                PostInfoDto postInfoDto =
                        bulk.postInfoMap().get(matchInfoDto.postId());

                if (postInfoDto == null) {
                    log.warn(
                            "[노쇼확정] Post 정보 없음 - matchId={}, postId={}",
                            matchId,
                            matchInfoDto.postId()
                    );
                    continue;
                }

                VerificationStatus status = meetVerification.getStatus();

                if (!meetVerification.isNoShowConfirmedSent()) {

                    // Kafka 발행을 afterCommit()으로 미뤄서 DB 롤백 시 알림 중복 발송 방지
                    if (status == VerificationStatus.BOTH_NO_SHOW) {
                        matchService.markBothNoShow(matchId);

                        notificationTargets.add(
                                new NoShowConfirmedNotificationTarget(postInfoDto.authorId(), matchId)
                        );
                        notificationTargets.add(
                                new NoShowConfirmedNotificationTarget(matchInfoDto.applicantId(), matchId)
                        );

                    } else if (status == VerificationStatus.GUEST_NO_SHOW) {
                        matchService.markApplicantNoShow(matchId);

                        notificationTargets.add(
                                new NoShowConfirmedNotificationTarget(matchInfoDto.applicantId(), matchId)
                        );

                    } else if (status == VerificationStatus.HOST_NO_SHOW) {
                        matchService.markAuthorNoShow(matchId);

                        notificationTargets.add(
                                new NoShowConfirmedNotificationTarget(postInfoDto.authorId(), matchId)
                        );
                    }

                    // sent 플래그는 트랜잭션 안에서 DB에 저장 (커밋되어야 의미 있음)
                    meetVerification.markNoShowConfirmedSent();
                }

                meetVerification.confirmNoShow();

                log.info(
                        "[노쇼확정] 처리 완료(커밋 전) - matchId={}, status={}, sent={}",
                        matchId,
                        meetVerification.getStatus(),
                        meetVerification.isNoShowConfirmedSent()
                );

            } catch (Exception e) {
                log.error(
                        "[노쇼확정] 처리 실패 - matchId={}, status={}, exception={}, message={}",
                        matchId,
                        meetVerification.getStatus(),
                        e.getClass().getSimpleName(),
                        e.getMessage(),
                        e
                );

                throw e;
            }
        }
    }

    // ===== 노쇼 확정 알림 발행 대상 record =====
    // afterCommit()에서 Kafka 발행 시 사용
    private record NoShowConfirmedNotificationTarget(
            Long userId,
            Long matchId
    ) { }

    // 특정 Post에 속한 노쇼/이의제기 상태 MeetVerification들을 관리자 노쇼 확정 상태로 일괄 정리
    // 등록자 노쇼처럼 Post 전체 책임이 확정되는 경우 사용
    @Override
    @Transactional
    public void confirmNoShowByPost(Long postId) {

        // MeetVerification에는 postId가 없으므로,
        // 먼저 postId에 속한 모든 matchId를 Match 도메인에서 조회
        List<Long> siblingMatchIds = matchService.getMatchIdsByPostId(postId);

        if (siblingMatchIds.isEmpty()) {
            return;
        }

        // 같은 Post에 속한 MeetVerification들을 한 번에 조회
        List<MeetVerification> siblingMvList =
                meetVerificationRepository.findAllByMatchIdIn(siblingMatchIds);

        for (MeetVerification mv : siblingMvList) {

            // 관리자 판정으로 노쇼 확정 가능한 상태만 변경
            // DONE 등 이미 정상 완료된 인증은 건드리지 않음
            if (mv.getStatus() == VerificationStatus.HOST_NO_SHOW
                    || mv.getStatus() == VerificationStatus.GUEST_NO_SHOW
                    || mv.getStatus() == VerificationStatus.BOTH_NO_SHOW
                    || mv.getStatus() == VerificationStatus.DISPUTE) {
                mv.confirmNoShowByAdmin();
            }
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

        // 연장 요청 -> 신청자만 가능,
        // 연장 수락/거절 -> 등록자만 가능
        if (!matchInfoDto.isApplicant(userId)) {
            throw new MeetException(ErrorCode.MEET_EXTEND_ONLY_APPLICANT);
        }

        // 요청 기준 Match가 MATCHED 상태인지 확인
        // 이미 취소/완료/노쇼/이의제기 상태라면 연장 요청을 허용하지 않는다.
        if (matchInfoDto.status() != MatchStatus.MATCHED) {
            throw new MeetException(ErrorCode.MEET_EXTEND_MATCH_NOT_MATCHED);
        }

        // 연장 요청은 약속시간 5분 전까지만 가능
        if (!LocalDateTime.now().isBefore(postInfoDto.meetAt().minusMinutes(EXTENSION_TIMEOUT_MINUTES))) {
            throw new MeetException(ErrorCode.MEET_EXTEND_BEFORE_MEET_AT);
        }

        // 같은 Post에 속한 활성 Match ID 목록을 조회
        // 연장 요청이 수락되면 이 목록에 해당하는 모든 MeetVerification에 연장이 전파
        List<Long> activeMatchIds = getActiveMatchIdsByPostId(matchInfoDto.postId());

        // 활성 Match가 없다면 정상적인 연장 요청 대상이 아니므로 예외 처리
        if (activeMatchIds.isEmpty()) {
            throw new MeetException(ErrorCode.MEET_EXTEND_MATCH_NOT_MATCHED);
        }

        // 같은 Post에 속한 활성 MeetVerification들을 PESSIMISTIC_WRITE 락으로 조회
        // 동시에 여러 신청자가 연장 요청을 보내더라도, 한 트랜잭션이 검증/REQUESTED 전파를 끝낼 때까지 다른 요청은 대기
        List<MeetVerification> activeMvList = meetVerificationRepository.findAllByMatchIdInWithLock(activeMatchIds);

        // 요청 기준 MeetVerification이 활성 목록에 포함되어 있는지 확인
        // 방어 코드: 요청한 matchId가 이미 취소/완료되어 activeMatchIds에서 빠진 경우를 막음
        boolean requestMvIsActive = activeMvList.stream()
                .anyMatch(mv -> mv.getMatchId().equals(matchId));

        // 요청한 Match가 활성 상태가 아니면 연장 요청을 진행할 수 없음
        if (!requestMvIsActive) {
            throw new MeetException(ErrorCode.MEET_EXTEND_MATCH_NOT_MATCHED);
        }

        // 같은 Post의 모든 활성 MeetVerification이 연장 요청 가능한 상태인지 검증
        // 한 명이 요청하더라도 전체 연장으로 처리되므로, 전체 상태가 요청 가능해야 함
        validateGroupExtensionRequestable(activeMvList);

        // 같은 Post의 모든 활성 MeetVerification에 동일한 연장 요청 상태를 기록
        // requesterId는 실제 요청자인 신청자 userId로 동일하게 저장
        for (MeetVerification mv : activeMvList) {

            // 각 Match별 MeetVerification에 REQUESTED 상태를 저장
            // 이렇게 해야 수락/거절/만료 시 같은 Post 전체를 일관되게 처리할 수 있음
            mv.requestExtension(userId);

            // 각 MeetVerification ID 기준으로 5분 타임아웃을 예약
            // 기존 구조가 MV ID를 ZSet member로 사용하므로, 전체 MV를 각각 예약
            reserveExtensionTimeout(mv);
        }

        // 연장 요청 알림은 등록자에게 1번만 발송
        // 신청자 중 누가 요청했든, 최종 응답자는 등록자이기 때문
        notificationPublisher.sendMeetExtendRequested(postInfoDto.authorId(), matchId);

        // 요청자 닉네임 조회
        String requesterNickname = userService.getUserInfo(userId).nickname();

        // 요청 기준 MeetVerification으로 응답을 생성
        return CreateMeetExtensionResponseDto.of(
                meetVerification,
                requesterNickname,
                postInfoDto.meetAt()
        );
    }

    // 만남 시간 연장 수락
    @Override
    @Transactional
    public AcceptMeetExtensionResponseDto acceptMeetExtension(Long userId, Long matchId) {

        // MeetVerification + MatchInfo + PostInfo 한 번에 조회
        MeetContext ctx = loadMeetContext(matchId);
        MatchInfoDto matchInfoDto = ctx.matchInfo();
        PostInfoDto postInfoDto = ctx.postInfo();

        // 요청자가 해당 Match의 당사자인지 검증
        validateParticipant(userId, matchInfoDto, postInfoDto);

        // 연장 요청 -> 신청자만 가능,
        // 연장 수락/거절 -> 등록자만 가능
        if (!userId.equals(postInfoDto.authorId())) {
            throw new MeetException(ErrorCode.MEET_EXTEND_ONLY_AUTHOR);
        }

        // 같은 Post에 속한 활성 Match ID 목록을 조회
        // 수락 시 연장 적용은 같은 Post의 모든 활성 MeetVerification에 전파
        List<Long> activeMatchIds = getActiveMatchIdsByPostId(matchInfoDto.postId());

        // 활성 Match가 없다면 수락 가능한 연장 요청도 없음
        if (activeMatchIds.isEmpty()) {
            throw new MeetException(ErrorCode.MEET_EXTEND_NO_ACTIVE_REQUEST);
        }

        // 같은 Post의 활성 MeetVerification들을 락으로 조회
        // 등록자의 수락 처리와 타임아웃 배치 또는 중복 수락 요청이 동시에 실행되는 것을 방지
        List<MeetVerification> activeMvList =
                meetVerificationRepository.findAllByMatchIdInWithLock(activeMatchIds);

        // 요청 기준 MeetVerification을 락이 걸린 목록에서 다시 찾음
        MeetVerification requestMeetVerification = activeMvList.stream()
                .filter(mv -> mv.getMatchId().equals(matchId))
                .findFirst()
                .orElseThrow(() -> new MeetException(ErrorCode.MEET_EXTEND_NO_ACTIVE_REQUEST));

        // 요청 기준 MeetVerification이 만료됐는지 먼저 확인
        // 만료됐다면 같은 Post의 연장 요청 전체를 EXPIRED 처리하고 예외를 던짐
        validateGroupExtensionNotExpired(matchInfoDto.postId(), requestMeetVerification);

        // 요청 기준 MV가 REQUESTED 상태인지 확인
        // REQUESTED가 아니라면 수락 가능한 활성 요청이 없는 상태
        if (requestMeetVerification.getExtensionStatus() != ExtensionStatus.REQUESTED) {
            throw new MeetException(ErrorCode.MEET_EXTEND_NO_ACTIVE_REQUEST);
        }

        // 등록자가 자기 자신이 요청한 연장을 수락하는 상황을 방어
        // 현재 정책상 신청자만 요청 가능하므로 보통 발생하지 않지만, 데이터 오염 방어용
        if (userId.equals(requestMeetVerification.getExtensionRequesterId())) {
            throw new MeetException(ErrorCode.MEET_EXTEND_SELF_RESPONSE);
        }

        for (MeetVerification mv : activeMvList) {

            // 그룹 연장은 같은 Post의 모든 활성 Match에 동일하게 적용, 따라서 REQUESTED 상태인 MV만이 아니라,
            // 현재 활성 MV 전체에 extendedMeetAt을 동일하게 세팅
            // 이렇게 해야 노쇼 판정 기준이 모두 원래 meetAt + 20분이 아니라, extendedMeetAt + 20분으로 통일됨
            mv.acceptExtension(postInfoDto.meetAt(), EXTENSION_MINUTES);

            // QR이 이미 발급된 경우 QR 만료 시각도 함께 연장
            // qrExpiresAt이 null이면 엔티티 메서드 내부에서 아무 작업도 하지 않음
            mv.extendQrExpiry(EXTENSION_MINUTES);

            // 수락이 끝났으므로 각 MV의 타임아웃 예약을 제거
            removeExtensionTimeout(mv);
        }

        // 연장 요청자에게 수락 알림을 보냄
        // 요청자는 한 명이므로, requestMv에 저장된 requesterId에게만 알림을 보냄
        notificationPublisher.sendMeetExtendAccepted(
                requestMeetVerification.getExtensionRequesterId(),
                matchId
        );

        // 요청 기준 MeetVerification으로 응답한다.
        // 실제 연장 적용은 같은 Post의 모든 활성 MV에 전파되어 있다.
        return AcceptMeetExtensionResponseDto.of(requestMeetVerification, postInfoDto.meetAt());
    }

    // 만남 시간 연장 거절
    @Override
    @Transactional
    public RejectMeetExtensionResponseDto rejectMeetExtension(Long userId, Long matchId) {

        // MeetVerification + MatchInfo + PostInfo 한 번에 조회
        MeetContext ctx = loadMeetContext(matchId);
        MatchInfoDto matchInfoDto = ctx.matchInfo();
        PostInfoDto postInfoDto = ctx.postInfo();

        // 요청자가 해당 Match의 당사자인지 검증한다.
        validateParticipant(userId, matchInfoDto, postInfoDto);

        // 최종 정책: 연장 거절은 등록자만 가능하다.
        if (!userId.equals(postInfoDto.authorId())) {
            throw new MeetException(ErrorCode.MEET_EXTEND_ONLY_AUTHOR);
        }

        // 같은 Post에 속한 활성 Match ID 목록을 조회한다.
        List<Long> activeMatchIds = getActiveMatchIdsByPostId(matchInfoDto.postId());

        // 활성 Match가 없다면 거절 가능한 연장 요청도 없음
        if (activeMatchIds.isEmpty()) {
            throw new MeetException(ErrorCode.MEET_EXTEND_NO_ACTIVE_REQUEST);
        }

        // 같은 Post의 활성 MeetVerification들을 락으로 조회
        // 수락/거절/타임아웃 처리가 같은 요청에 대해 동시에 실행되는 상황을 줄임
        List<MeetVerification> activeMvList =
                meetVerificationRepository.findAllByMatchIdInWithLock(activeMatchIds);

        // 요청 기준 MeetVerification을 락이 걸린 목록에서 다시 찾음
        MeetVerification requestMeetVerification = activeMvList.stream()
                .filter(mv -> mv.getMatchId().equals(matchId))
                .findFirst()
                .orElseThrow(() -> new MeetException(ErrorCode.MEET_EXTEND_NO_ACTIVE_REQUEST));

        // 요청 기준 MeetVerification이 만료됐는지 먼저 확인한다.
        // 이미 만료된 요청이면 같은 Post 전체 요청을 EXPIRED 처리하고 예외를 던진다.
        validateGroupExtensionNotExpired(matchInfoDto.postId(), requestMeetVerification);

        // 요청 기준 MV가 REQUESTED 상태인지 확인한다.
        // REQUESTED 상태가 아니면 거절할 수 있는 활성 요청이 없다.
        if (requestMeetVerification.getExtensionStatus() != ExtensionStatus.REQUESTED) {
            throw new MeetException(ErrorCode.MEET_EXTEND_NO_ACTIVE_REQUEST);
        }

        // 등록자가 자기 자신이 요청한 연장을 거절하는 상황을 방어한다.
        // 현재 정책상 신청자만 요청 가능하므로 보통 발생하지 않지만, 데이터 오염 방어용이다.
        if (userId.equals(requestMeetVerification.getExtensionRequesterId())) {
            throw new MeetException(ErrorCode.MEET_EXTEND_SELF_RESPONSE);
        }

        // 같은 Post의 모든 REQUESTED 상태 MeetVerification을 거절 처리한다.
        for (MeetVerification mv : activeMvList) {

            // REQUESTED 상태인 항목만 REJECTED 처리한다.
            if (mv.getExtensionStatus() == ExtensionStatus.REQUESTED) {
                mv.rejectExtension();
            }

            // 거절이 끝났으므로 각 MV의 타임아웃 예약을 제거한다.
            removeExtensionTimeout(mv);
        }

        // 연장 요청자에게 거절 알림을 보낸다.
        notificationPublisher.sendMeetExtendRejected(
                requestMeetVerification.getExtensionRequesterId(),
                matchId
        );

        // 요청 기준 MeetVerification으로 응답한다.
        return RejectMeetExtensionResponseDto.from(requestMeetVerification);
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

        // REQUESTED 상태이면서 요청 시각이 5분보다 오래된 MeetVerification을 조회
        // 즉, 등록자가 제한 시간 안에 수락/거절하지 않은 연장 요청
        LocalDateTime expireThreshold = LocalDateTime.now().minusMinutes(EXTENSION_TIMEOUT_MINUTES);

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
            MatchInfoDto matchInfo = matchService.getMatchInfo(expiredMv.getMatchId());

            // 같은 Post를 이미 처리했다면 중복 만료 처리를 하지 않음
            if (!processedPostIds.add(matchInfo.postId())) {
                continue;
            }

            // 같은 Post에 속한 모든 Match ID를 조회
            List<Long> siblingMatchIds = matchService.getMatchIdsByPostId(matchInfo.postId());

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
                removeExtensionTimeout(mv);
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

    // 사용자 노쇼 예정 매칭 목록 조회
    // 내가 등록자 또는 신청자로 참여한 매칭 중 노쇼 예정 상태인 것만 반환
    // 이의제기 화면 드롭다운에 표시할 매칭 목록 제공용
    @Override
    public List<NoShowMatchResponseDto> getNoShowMatchesForUser(Long userId) {

        // 1. 내가 참여한 전체 매칭 ID 목록 조회 (matchService 통해 서비스투서비스)
        List<Long> myMatchIds = matchService.getAllMatchIdsByUserId(userId);

        // 2. 매칭이 없으면 빈 리스트 반환
        if (myMatchIds.isEmpty()) {
            return List.of();
        }

        // 3. 내 매칭 중 노쇼 예정 상태인 MeetVerification만 조회
        // NO_SHOW_STATUSES = HOST_NO_SHOW, GUEST_NO_SHOW, BOTH_NO_SHOW
        List<MeetVerification> noShowMvList = meetVerificationRepository
                .findAllByMatchIdInAndStatusIn(myMatchIds, NO_SHOW_STATUSES);

        // 4. DTO 변환
        return noShowMvList.stream()
                .map(NoShowMatchResponseDto::from)
                .toList();
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
    private void issueQrTokenIfNeeded(MeetVerification meetVerification, Long postId) {

        // 현재 MeetVerification에 이미 QR 토큰이 있으면 중복 발급 X
        if (meetVerification.getQrToken() != null) {
            return;
        }

        // 등록자와 신청자 양측 장소 인증이 끝나지 않았으면 QR을 발급 X
        if (meetVerification.getAuthorPlaceVerifiedAt() == null
                || meetVerification.getApplicantPlaceVerifiedAt() == null) {
            return;
        }

        // 같은 Post에 이미 발급된 공통 QR 토큰이 있으면 재사용
        // 없으면 새 공통 QR 토큰을 생성
        String sharedToken = getPostQrTokenOwner(postId)
                .map(MeetVerification::getQrToken)
                .orElseGet(() -> "hp_qr_" + UUID.randomUUID().toString().replace("-", ""));

        // 등록자와 신청자의 장소 인증 시각 중 더 늦은 시각을 기준으로 QR 만료 시간을 계산
        LocalDateTime placeVerifiedCompletedAt = meetVerification.getAuthorPlaceVerifiedAt()
                .isAfter(meetVerification.getApplicantPlaceVerifiedAt())
                ? meetVerification.getAuthorPlaceVerifiedAt()
                : meetVerification.getApplicantPlaceVerifiedAt();

        // QR 만료 시각은 양측 장소 인증 완료 시각 기준 30분 이후
        LocalDateTime expiresAt =
                placeVerifiedCompletedAt.plusMinutes(QR_TOKEN_VALIDITY_MINUTES);

        // 현재 MeetVerification에 Post 공통 QR 토큰과 만료 시각을 저장
        meetVerification.issueQrToken(sharedToken, expiresAt);
    }

    // postId 기준으로 이미 발급된 공통 QR 토큰 Owner를 조회
    // QR 토큰이 저장된 MeetVerification 하나를 공통 토큰 owner로 사용
    private Optional<MeetVerification> getPostQrTokenOwner(Long postId) {

        // 같은 Post에 속한 모든 Match ID를 조회
        List<Long> siblingMatchIds = matchService.getMatchIdsByPostId(postId);

        // 같은 Post에 Match가 없다면 공통 QR 토큰도 존재할 수 없음
        if (siblingMatchIds.isEmpty()) {
            return Optional.empty();
        }

        // 같은 Post에 속한 MeetVerification을 한 번에 조회
        List<MeetVerification> siblingMvList =
                meetVerificationRepository.findAllByMatchIdIn(siblingMatchIds);

        // 조회된 목록에서 QR 토큰을 가진 MeetVerification을 찾기
        return findPostQrTokenOwner(siblingMvList);
    }

    // MeetVerification 목록에서 QR 토큰을 가진 항목 하나를 찾기,
    // 이미 발급된 Post 공통 QR 토큰이 있는지 확인하는 공통메서드
    // 없으면 null을 반환해 호출부에서 최초 발급
    private Optional<MeetVerification> findPostQrTokenOwner(List<MeetVerification> meetVerifications) {

        // QR 토큰이 null이 아닌 MeetVerification을 하나 찾는다.
        return meetVerifications.stream()
                .filter(mv -> mv.getQrToken() != null)
                .findFirst();
    }

    // ① MeetVerification + MatchInfo + PostInfo 한 번에 조회
    // 사용 위치: createPlaceVerification, getMeetQr, getMeetVerification,
    //            createMeetExtension, acceptMeetExtension, rejectMeetExtension, getMeetExtension
    private record MeetContext(
            MeetVerification meetVerification,
            MatchInfoDto matchInfo,
            PostInfoDto postInfo
    ) {
    }

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

    // 그룹 연장 요청의 만료 여부를 확인
    // 만료된 상태라면 같은 Post의 모든 REQUESTED MeetVerification을 EXPIRED 처리하고 예외를 던짐
    private void validateGroupExtensionNotExpired(Long postId, MeetVerification meetVerification) {

        // 요청 기준 MV가 REQUESTED 상태이고 5분 타임아웃이 지났는지 확인
        if (meetVerification.getExtensionStatus() == ExtensionStatus.REQUESTED
                && meetVerification.isExtensionExpired(EXTENSION_TIMEOUT_MINUTES)) {

            // 같은 Post에 속한 모든 Match ID를 조회
            List<Long> siblingMatchIds = matchService.getMatchIdsByPostId(postId);

            // 요청 기준 MV가 이미 만료된 상태이므로, 대상 Match가 없더라도 수락/거절 흐름은 차단
            if (siblingMatchIds.isEmpty()) {
                throw new MeetException(ErrorCode.MEET_EXTEND_EXPIRED);
            }

            // 같은 Post에 속한 모든 MeetVerification을 PESSIMISTIC_WRITE 락으로 조회
            // 수락/거절 요청과 만료 처리가 동시에 들어와도, 한쪽 트랜잭션이 상태 변경을 끝낼 때까지 다른 쪽은 대기
            List<MeetVerification> siblingMvList =
                    meetVerificationRepository.findAllByMatchIdInWithLock(siblingMatchIds);

            // 같은 Post의 REQUESTED 상태를 모두 EXPIRED 처리
            for (MeetVerification mv : siblingMvList) {

                // REQUESTED 상태인 항목만 만료 처리
                if (mv.getExtensionStatus() == ExtensionStatus.REQUESTED) {
                    mv.expireExtension();
                }

                // 각 MV의 타임아웃 예약을 제거
                removeExtensionTimeout(mv);
            }

            // 호출부에 만료 예외를 알림
            throw new MeetException(ErrorCode.MEET_EXTEND_EXPIRED);
        }
    }

    // 연장 요청 타임아웃을 ZSet에 예약
    private void reserveExtensionTimeout(MeetVerification meetVerification) {

        // 현재 시각 + 5분을 Unix Timestamp로 변환
        double timeoutScore = LocalDateTime.now()
                .plusMinutes(EXTENSION_TIMEOUT_MINUTES)
                .toEpochSecond(KST);

        // ZSet member는 MeetVerification ID
        // 기존 스케줄러 구조가 MeetVerification ID를 기준으로 만료 대상을 찾기 때문
        redisTemplate.opsForZSet().add(
                MeetRedisZSetKeys.EXTENSION_TIMEOUT,
                String.valueOf(meetVerification.getId()),
                timeoutScore
        );
    }

    // 연장 요청 타임아웃 예약을 ZSet에서 제거
    private void removeExtensionTimeout(MeetVerification meetVerification) {

        // ZSet member는 MeetVerification ID
        redisTemplate.opsForZSet().remove(
                MeetRedisZSetKeys.EXTENSION_TIMEOUT,
                String.valueOf(meetVerification.getId())
        );
    }

    // 장소 인증 완료 알림을 정책에 맞게 발송
    // 1:1 매칭 -> 인증한 사람을 제외한 상대방에게만 발송
    // 그룹 매칭 -> 인증한 사람을 제외한 모임 참여자 전원에게 발송
    private void notifyPlaceVerifiedToParticipants(
            Long verifierId,
            Long postId,
            Long verifiedMatchId,
            Long authorId
    ) {
        // 같은 Post의 모든 Match ID 조회
        List<Long> activeMatchIds = matchService.getMatchIdsByPostId(postId);

        if (activeMatchIds.isEmpty()) {
            return;
        }

        // matchId별 신청자 정보 벌크 조회
        Map<Long, MatchInfoDto> siblingInfos = matchService.getMatchInfos(activeMatchIds);

        // 인증자가 등록자가 아니라면 등록자에게 알림을 보냄
        // 등록자는 HOST이므로 실제 인증이 일어난 matchId를 relatedId로 사용
        if (!verifierId.equals(authorId)) {
            notificationPublisher.sendPlaceVerified(authorId, verifiedMatchId);
        }

        // 모든 신청자에게 알림을 보냄
        // 단, 인증한 본인에게는 중복 알림을 보내지 않음
        siblingInfos.forEach((siblingMatchId, info) -> {
            Long applicantId = info.applicantId();

            if (!applicantId.equals(verifierId)) {
                notificationPublisher.sendPlaceVerified(applicantId, siblingMatchId);
            }
        });
    }

    // 같은 Post에 속한 활성 Match ID 목록을 조회
    // 활성 Match란 현재 만남이 진행 중인 MATCHED 상태의 Match를 의미
    private List<Long> getActiveMatchIdsByPostId(Long postId) {

        // 같은 Post에 속한 모든 Match ID를 조회
        List<Long> siblingMatchIds = matchService.getMatchIdsByPostId(postId);

        // Match ID가 없으면 빈 리스트를 반환
        if (siblingMatchIds.isEmpty()) {
            return List.of();
        }

        // Match 정보를 벌크 조회
        Map<Long, MatchInfoDto> siblingMatchInfoMap =
                matchService.getMatchInfos(siblingMatchIds);

        // MATCHED 상태인 Match ID만 필터링해서 반환
        return siblingMatchInfoMap.entrySet().stream()
                .filter(entry -> entry.getValue().status() == MatchStatus.MATCHED)
                .map(Map.Entry::getKey)
                .toList();
    }

    // 같은 Post의 모든 활성 MeetVerification이 연장 요청 가능한 상태인지 검증
    // 한 신청자가 요청해도 전체 연장으로 처리되므로, 전체 상태가 요청 가능해야 함
    private void validateGroupExtensionRequestable(List<MeetVerification> activeMvList) {

        // 활성 MV가 없다면 연장 요청을 진행할 수 없음
        if (activeMvList.isEmpty()) {
            throw new MeetException(ErrorCode.MEET_EXTEND_MATCH_NOT_MATCHED);
        }

        // 같은 Post의 모든 활성 MV 상태를 확인
        for (MeetVerification mv : activeMvList) {

            // 이미 연장이 수락되어 extendedMeetAt이 설정된 상태라면 재연장을 허용하지 않음
            if (mv.isExtended()) {
                throw new MeetException(ErrorCode.MEET_EXTEND_ALREADY_ACCEPTED);
            }

            // 이미 거절된 요청이 있다면 정책상 재요청을 허용하지 않음
            if (mv.getExtensionStatus() == ExtensionStatus.REJECTED) {
                throw new MeetException(ErrorCode.MEET_EXTEND_ALREADY_REJECTED);
            }

            // 아직 만료되지 않은 REQUESTED 요청이 있으면 중복 요청으로 판단
            if (mv.getExtensionStatus() == ExtensionStatus.REQUESTED
                    && !mv.isExtensionExpired(EXTENSION_TIMEOUT_MINUTES)) {
                throw new MeetException(ErrorCode.MEET_EXTEND_ALREADY_REQUESTED);
            }

            // REQUESTED 상태지만 이미 만료된 요청이면 EXPIRED 처리 후 새 요청을 허용
            if (mv.getExtensionStatus() == ExtensionStatus.REQUESTED
                    && mv.isExtensionExpired(EXTENSION_TIMEOUT_MINUTES)) {
                mv.expireExtension();
                removeExtensionTimeout(mv);
            }
        }
    }

    // ④ matchId 목록으로 MatchInfo + PostInfo 벌크 조회
    // 사용 위치: judgeGpsNoShow, judgeNoShowConfirmed
    private record BulkMatchContext(
            Map<Long, MatchInfoDto> matchInfoMap,
            Map<Long, PostInfoDto> postInfoMap
    ) {
    }

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

        // 같은 Post에 속한 활성 Match ID만 조회
        // 완료/취소/노쇼/이의제기 상태의 Match는 장소 인증 전파 대상이 아님
        List<Long> activeMatchIds = getActiveMatchIdsByPostId(postId);

        if (activeMatchIds.isEmpty()) {
            return;
        }

        // 같은 Post의 활성 Match에 속한 MeetVerification만 조회
        List<MeetVerification> mvList =
                meetVerificationRepository.findAllByMatchIdIn(activeMatchIds);

        for (MeetVerification mv : mvList) {

            // 이미 등록자 인증이 완료된 항목은 중복 처리하지 않음
            if (mv.isAuthorPlaceVerified()) {
                continue;
            }

            // 등록자 장소 인증 완료 시각을 기록하고,
            // 신청자도 인증 완료 상태면 VERIFIED로 전환
            mv.verifyAuthorPlace();
        }
    }
}