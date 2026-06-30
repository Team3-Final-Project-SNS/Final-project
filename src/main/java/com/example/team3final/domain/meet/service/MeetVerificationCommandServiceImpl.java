package com.example.team3final.domain.meet.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.MeetException;
import com.example.team3final.common.utils.GpsUtils;
import com.example.team3final.domain.chat.service.ChatInternalService;
import com.example.team3final.domain.location.service.UserLocationCleanupService;
import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.service.MatchInternalService;
import com.example.team3final.domain.match.service.MatchLifecycleService;
import com.example.team3final.domain.meet.context.MeetVerificationContext;
import com.example.team3final.domain.meet.dto.request.PlaceVerificationRequestDto;
import com.example.team3final.domain.meet.dto.request.QrScanRequestDto;
import com.example.team3final.domain.meet.dto.response.*;
import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.enums.ExtensionStatus;
import com.example.team3final.domain.meet.enums.VerificationStatus;
import com.example.team3final.domain.meet.repository.MeetVerificationRepository;
import com.example.team3final.domain.meet.service.support.MeetExtensionSupport;
import com.example.team3final.domain.meet.service.support.MeetQrSupport;
import com.example.team3final.domain.meet.service.support.MeetVerificationContextReader;
import com.example.team3final.domain.meet.service.support.MeetVerificationPolicy;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.dto.response.PostInfoDto;
import com.example.team3final.domain.user.service.UserInternalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// MeetVerification 도메인의 사용자 요청 기반 인증/연장 변경 작업을 담당하는 서비스
@Service
@RequiredArgsConstructor
@Transactional
public class MeetVerificationCommandServiceImpl implements MeetVerificationCommandService {

    private final MeetVerificationRepository meetVerificationRepository;
    private final MatchInternalService matchInternalService;
    private final MatchLifecycleService matchLifecycleService;
    private final ChatInternalService chatInternalService;
    private final UserInternalService userInternalService;
    private final UserLocationCleanupService userLocationCleanupService;
    private final NotificationPublisher notificationPublisher;
    private final MeetVerificationContextReader contextReader;
    private final MeetExtensionSupport meetExtensionSupport;
    private final MeetQrSupport meetQrSupport;
    private final MeetOverdueReservationService meetOverdueReservationService;

    // GPS 장소 인증
    @Override
    @Transactional
    public PlaceVerificationResponseDto createPlaceVerification(
            Long userId,
            Long matchId,
            PlaceVerificationRequestDto requestDto) {

        // MeetVerification + MatchInfo + PostInfo 한 번에 조회
        MeetVerificationContext ctx = contextReader.loadMeetContext(matchId);
        MeetVerification meetVerification = ctx.meetVerification();
        MatchInfoDto matchInfo = ctx.matchInfo();
        PostInfoDto postInfo = ctx.postInfo();

        // 매칭 당사자 검증 (등록자 or 신청자인지)
        contextReader.validateParticipant(userId, matchInfo, postInfo);

        // 노쇼 예정, 이의제기 중, 노쇼 확정 상태에서는 GPS 재인증 차단
        if (MeetVerificationPolicy.NO_SHOW_STATUSES.contains(meetVerification.getStatus())
                || meetVerification.getStatus() == VerificationStatus.DISPUTE
                || meetVerification.getStatus() == VerificationStatus.NO_SHOW_CONFIRMED) {
            throw new MeetException(ErrorCode.GPS_NOT_VERIFICATION_TIME);
        }

        LocalDateTime now = LocalDateTime.now();

        // 장소 인증 기준 시각은 연장 수락 시 연장된 만남 시각을 우선 사용
        LocalDateTime effectiveMeetAt = meetVerification.getExtendedMeetAt() != null
                ? meetVerification.getExtendedMeetAt()
                : postInfo.meetAt();

        LocalDateTime verificationStartTime = effectiveMeetAt.minusMinutes(
                MeetVerificationPolicy.VERIFICATION_BEFORE_MINUTES);
        LocalDateTime verificationEndTime = effectiveMeetAt.plusMinutes(
                MeetVerificationPolicy.VERIFICATION_AFTER_MINUTES);

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

        // 정책 반경 50m에 GPS 오차 허용 범위 10m를 더한 서버 판정 반경 초과 여부 확인
        if (distanceMeters > MeetVerificationPolicy.PLACE_VERIFICATION_RADIUS_METERS) {
            // 서버 판정 반경 60m 밖이면 장소 인증 미처리 및 예외 발생
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

        // QR은 전체 GPS 완료 또는 만남 시간 기준 3분 경과 시 발급
        issueQrTokenIfEligible(matchInfo.postId(), effectiveMeetAt, now);

        // VERIFIED 여부 확인
        // 전파 후 이 MeetVerification의 상태가 바뀌었을 수 있으므로 엔티티에서 다시 읽음
        boolean bothVerified = meetVerification.getStatus() == VerificationStatus.VERIFIED;

        // 14. 장소 인증 완료 알림
        // 장소 인증 완료 알림.
        // 정책:
        // - 모든 매칭: 인증한 사람을 제외한 모임 참여자에게 발송
        notifyPlaceVerifiedToParticipants(
                userId,
                matchInfo.postId(),
                matchId,
                postInfo.authorId()
        );

        return PlaceVerificationResponseDto.of(meetVerification, distanceMeters, bothVerified);
    }

    private void issueQrTokenIfEligible(Long postId, LocalDateTime effectiveMeetAt, LocalDateTime now) {
        List<Long> activeMatchIds = contextReader.getActiveMatchIdsByPostId(postId);
        if (activeMatchIds.isEmpty()) {
            return;
        }

        List<MeetVerification> siblingMvList = meetVerificationRepository.findAllByMatchIdIn(activeMatchIds);
        boolean authorVerified = siblingMvList.stream().anyMatch(MeetVerification::isAuthorPlaceVerified);
        if (!authorVerified) {
            return;
        }

        boolean allPlaceVerified = siblingMvList.stream()
                .allMatch(mv -> mv.isAuthorPlaceVerified() && mv.isApplicantPlaceVerified());
        boolean fallbackTimeReached = !now.isBefore(
                effectiveMeetAt.plusMinutes(MeetVerificationPolicy.QR_FALLBACK_AFTER_MINUTES)
        );

        if (!allPlaceVerified && !fallbackTimeReached) {
            return;
        }

        meetQrSupport.issuePostQrTokenIfNeeded(postId, now);
    }

    // QR 스캔 (신청자 전용)
    @Override
    @Transactional
    public QrScanResponseDto createQrScan(Long userId, Long matchId, QrScanRequestDto requestDto) {

        MatchInfoDto matchInfo = matchInternalService.getMatchInfo(matchId);
        PostInfoDto postInfo = contextReader.loadMeetContext(matchId).postInfo();

        if (!matchInfo.isApplicant(userId)) {
            throw new MeetException(ErrorCode.SCAN_NOT_APPLICANT);
        }

        // 그룹 QR 완료 판정이 엇갈리지 않도록 같은 게시글의 활성 인증 row를 먼저 잠금
        List<Long> activeMatchIds = contextReader.getActiveMatchIdsByPostId(matchInfo.postId());
        List<MeetVerification> siblingMvList =
                meetVerificationRepository.findAllByMatchIdInWithLock(activeMatchIds);
        MeetVerification meetVerification = siblingMvList.stream()
                .filter(mv -> mv.getMatchId().equals(matchId))
                .findFirst()
                .orElseThrow(() -> new MeetException(ErrorCode.MEET_VERIFICATION_NOT_FOUND));

        if (meetVerification.getStatus() == VerificationStatus.DONE) {
            throw new MeetException(ErrorCode.GPS_ALREADY_VERIFIED);
        }

        if (!meetVerification.isApplicantPlaceVerified()) {
            throw new MeetException(ErrorCode.QR_PLACE_VERIFICATION_REQUIRED);
        }

        // 공통 QR 토큰 owner가 이미 완료된 match row일 수 있어 게시글 전체에서 토큰을 조회
        MeetVerification tokenOwner = meetQrSupport.getPostQrTokenOwner(matchInfo.postId())
                .orElseThrow(() -> new MeetException(ErrorCode.QR_PLACE_VERIFICATION_REQUIRED));

        if (tokenOwner.isQrExpired()) {
            throw new MeetException(ErrorCode.QR_EXPIRED);
        }

        if (!requestDto.getQrToken().equals(tokenOwner.getQrToken())) {
            throw new MeetException(ErrorCode.SCAN_INVALID_QR_TOKEN);
        }

        // 환급 포인트 응답을 위해 완료 전 신청자 예치금을 보관
        Match match = matchInternalService.getMatchById(matchId);
        int refundedPoint = match.getApplicantDeposit();

        // QR 인증 1건은 해당 신청자의 MeetVerification과 Match만 즉시 완료
        meetVerification.meetVerifiedDone();
        userLocationCleanupService.deleteLocationsByMatchId(matchId);

        boolean isLastCompletedMatch = matchLifecycleService.completeSingleMatch(matchId);

        String authorNickname = userInternalService.getUserInfo(postInfo.authorId()).nickname();
        String applicantNickname = userInternalService.getUserInfo(matchInfo.applicantId()).nickname();
        notificationPublisher.sendMeetCompleted(matchInfo.applicantId(), matchId, authorNickname);
        notificationPublisher.sendMeetCompletedForAuthor(postInfo.authorId(), matchId, applicantNickname);

        // 모든 활성 match가 끝난 경우에만 게시글 완료와 채팅방 비활성화 예약
        if (isLastCompletedMatch) {
            matchLifecycleService.completePostIfAllMatchesCompleted(matchInfo.postId());
            chatInternalService.scheduleChatRoomDeactivation(matchInfo.postId());
        }

        return QrScanResponseDto.of(
                matchId,
                meetVerification,
                MatchStatus.COMPLETED,
                refundedPoint
        );
    }

    @Override
    @Transactional
    public CreateMeetExtensionResponseDto createMeetExtension(Long userId, Long matchId) {

        // MeetVerification + MatchInfo + PostInfo 한 번에 조회
        MeetVerificationContext ctx = contextReader.loadMeetContext(matchId);
        MeetVerification meetVerification = ctx.meetVerification();
        MatchInfoDto matchInfoDto = ctx.matchInfo();
        PostInfoDto postInfoDto = ctx.postInfo();

        // 매칭 당사자 확인
        contextReader.validateParticipant(userId, matchInfoDto, postInfoDto);

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
        if (!LocalDateTime.now().isBefore(postInfoDto.meetAt().minusMinutes(MeetVerificationPolicy.EXTENSION_TIMEOUT_MINUTES))) {
            throw new MeetException(ErrorCode.MEET_EXTEND_BEFORE_MEET_AT);
        }

        // 같은 Post에 속한 활성 Match ID 목록을 조회
        // 연장 요청이 수락되면 이 목록에 해당하는 모든 MeetVerification에 연장이 전파
        List<Long> activeMatchIds = contextReader.getActiveMatchIdsByPostId(matchInfoDto.postId());

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
        meetExtensionSupport.validateGroupExtensionRequestable(activeMvList);

        // 같은 Post의 모든 활성 MeetVerification에 동일한 연장 요청 상태를 기록
        // requesterId는 실제 요청자인 신청자 userId로 동일하게 저장
        for (MeetVerification mv : activeMvList) {

            // 각 Match별 MeetVerification에 REQUESTED 상태를 저장
            // 이렇게 해야 수락/거절/만료 시 같은 Post 전체를 일관되게 처리할 수 있음
            mv.requestExtension(userId);

            // 각 MeetVerification ID 기준으로 5분 타임아웃을 예약
            // 기존 구조가 MV ID를 ZSet member로 사용하므로, 전체 MV를 각각 예약
            meetExtensionSupport.reserveExtensionTimeout(mv);
        }

        // 연장 요청 알림은 등록자에게 1번만 발송
        // 신청자 중 누가 요청했든, 최종 응답자는 등록자이기 때문
        notificationPublisher.sendMeetExtendRequested(postInfoDto.authorId(), matchId);

        // 요청자 닉네임 조회
        String requesterNickname = userInternalService.getUserInfo(userId).nickname();

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
        MeetVerificationContext ctx = contextReader.loadMeetContext(matchId);
        MatchInfoDto matchInfoDto = ctx.matchInfo();
        PostInfoDto postInfoDto = ctx.postInfo();

        // 요청자가 해당 Match의 당사자인지 검증
        contextReader.validateParticipant(userId, matchInfoDto, postInfoDto);

        // 연장 요청 -> 신청자만 가능,
        // 연장 수락/거절 -> 등록자만 가능
        if (!userId.equals(postInfoDto.authorId())) {
            throw new MeetException(ErrorCode.MEET_EXTEND_ONLY_AUTHOR);
        }

        // 같은 Post에 속한 활성 Match ID 목록을 조회
        // 수락 시 연장 적용은 같은 Post의 모든 활성 MeetVerification에 전파
        List<Long> activeMatchIds = contextReader.getActiveMatchIdsByPostId(matchInfoDto.postId());

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
        meetExtensionSupport.validateGroupExtensionNotExpired(matchInfoDto.postId(), requestMeetVerification);

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
            // 이렇게 해야 노쇼 판정 기준이 모두 원래 meetAt + 10분이 아니라, extendedMeetAt + 10분으로 통일됨
            mv.acceptExtension(postInfoDto.meetAt(), MeetVerificationPolicy.EXTENSION_MINUTES);

            // QR이 이미 발급된 경우 QR 만료 시각도 함께 연장
            // qrExpiresAt이 null이면 엔티티 메서드 내부에서 아무 작업도 하지 않음
            mv.extendQrExpiry(MeetVerificationPolicy.EXTENSION_MINUTES);

            // 수락이 끝났으므로 각 MV의 타임아웃 예약을 제거
            meetExtensionSupport.removeExtensionTimeout(mv);
        }

        // DB 커밋 이후 Redis 재예약에 사용할 값을 복사한다.
        Long postId = matchInfoDto.postId();
        List<Long> matchIdsForReservation = List.copyOf(activeMatchIds);

        // 모든 활성 MeetVerification에 저장된 extendedMeetAt과 동일한 값이다.
        LocalDateTime extendedMeetAt = postInfoDto.meetAt()
                .plusMinutes(MeetVerificationPolicy.EXTENSION_MINUTES);

        // DB 연장이 정상 커밋된 이후에만 Redis의 10분 경과 알림 시각을 갱신한다.
        // DB가 롤백됐는데 Redis 예약만 변경되는 불일치를 방지한다.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                meetOverdueReservationService.rescheduleAfterExtension(
                        postId,
                        matchIdsForReservation,
                        extendedMeetAt
                );
            }
        });

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
        MeetVerificationContext ctx = contextReader.loadMeetContext(matchId);
        MatchInfoDto matchInfoDto = ctx.matchInfo();
        PostInfoDto postInfoDto = ctx.postInfo();

        // 요청자가 해당 Match의 당사자인지 검증한다.
        contextReader.validateParticipant(userId, matchInfoDto, postInfoDto);

        // 최종 정책: 연장 거절은 등록자만 가능하다.
        if (!userId.equals(postInfoDto.authorId())) {
            throw new MeetException(ErrorCode.MEET_EXTEND_ONLY_AUTHOR);
        }

        // 같은 Post에 속한 활성 Match ID 목록을 조회한다.
        List<Long> activeMatchIds = contextReader.getActiveMatchIdsByPostId(matchInfoDto.postId());

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
        meetExtensionSupport.validateGroupExtensionNotExpired(matchInfoDto.postId(), requestMeetVerification);

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
            meetExtensionSupport.removeExtensionTimeout(mv);
        }

        // 연장 요청자에게 거절 알림을 보낸다.
        notificationPublisher.sendMeetExtendRejected(
                requestMeetVerification.getExtensionRequesterId(),
                matchId
        );

        // 요청 기준 MeetVerification으로 응답한다.
        return RejectMeetExtensionResponseDto.from(requestMeetVerification);
    }

    // 등록자 GPS 인증을 같은 postId의 모든 MeetVerification에 전파
    private void propagateAuthorVerification(Long postId) {

        // 같은 Post에 속한 활성 Match ID만 조회
        // 완료/취소/노쇼/이의제기 상태의 Match는 장소 인증 전파 대상이 아님
        List<Long> activeMatchIds = contextReader.getActiveMatchIdsByPostId(postId);

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

    // 장소 인증 완료 알림을 정책에 맞게 발송
    // 인증한 사람을 제외한 모임 참여자에게 장소 인증 완료 알림 발송
    private void notifyPlaceVerifiedToParticipants(
            Long verifierId,
            Long postId,
            Long verifiedMatchId,
            Long authorId
    ) {
        String verifierNickname = userInternalService.getUserInfo(verifierId).nickname();

        // 같은 Post의 활성 Match ID만 조회
        List<Long> activeMatchIds = matchInternalService.getActiveMatchIdsByPostId(postId);

        if (activeMatchIds.isEmpty()) {
            return;
        }

        // matchId별 신청자 정보 벌크 조회
        Map<Long, MatchInfoDto> siblingInfos = matchInternalService.getMatchInfos(activeMatchIds);

        // 인증자가 등록자가 아니라면 등록자에게 알림을 보냄
        // 등록자는 HOST이므로 실제 인증이 일어난 matchId를 relatedId로 사용
        if (!verifierId.equals(authorId)) {
            notificationPublisher.sendPlaceVerified(authorId, verifiedMatchId, verifierNickname);
        }

        // 모든 신청자에게 알림을 보냄
        // 단, 인증한 본인에게는 중복 알림을 보내지 않음
        siblingInfos.forEach((siblingMatchId, info) -> {
            Long applicantId = info.applicantId();

            if (!applicantId.equals(verifierId)) {
                notificationPublisher.sendPlaceVerified(applicantId, siblingMatchId, verifierNickname);
            }
        });
    }
}
