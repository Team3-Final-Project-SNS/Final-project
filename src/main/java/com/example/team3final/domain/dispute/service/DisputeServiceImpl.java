package com.example.team3final.domain.dispute.service;

import com.example.team3final.common.exception.DisputeException;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.domain.admin.service.AdminService;
import com.example.team3final.domain.dispute.dto.request.CreateDisputeRequestDto;
import com.example.team3final.domain.dispute.dto.response.CreateDisputeResponseDto;
import com.example.team3final.domain.dispute.dto.response.DisputeResponseDto;
import com.example.team3final.domain.dispute.entity.Dispute;
import com.example.team3final.domain.dispute.enums.DisputeStatus;
import com.example.team3final.domain.dispute.repository.DisputeRepository;
import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.service.MatchService;
import com.example.team3final.domain.meet.dto.response.MeetVerificationResponseDto;
import com.example.team3final.domain.meet.enums.VerificationStatus;
import com.example.team3final.domain.meet.service.MeetVerificationService;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.dto.response.PostInfoDto;
import com.example.team3final.domain.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DisputeServiceImpl implements DisputeService {

    private final DisputeRepository disputeRepository;
    private final MatchService matchService;
    private final PostService postService;
    private final MeetVerificationService meetVerificationService;
    private final AdminService adminService;
    private final NotificationPublisher notificationPublisher;

    /**
     * 이의제기 제출
     */
    @Override
    @Transactional
    public CreateDisputeResponseDto createDispute(Long matchId, Long userId, CreateDisputeRequestDto request) {

        // 1. 매칭 존재 확인
        MatchInfoDto match = matchService.getMatchInfo(matchId);

        // 2. 등록자 조회
        PostInfoDto post = postService.getPostInfo(match.postId());
        Long authorId = post.authorId();

        // 3. 당사자 검증
        if (!match.isParticipant(userId, authorId)) {
            throw new DisputeException(ErrorCode.MATCH_NOT_PARTICIPANT);
        }

        // 4. 만남 인증 정보 조회
        MeetVerificationResponseDto meet = meetVerificationService.getMeetVerification(userId, matchId);

        // 5. 노쇼 예정 상태 검증
        VerificationStatus status = meet.verificationStatus();
        boolean isNoShow = status == VerificationStatus.HOST_NO_SHOW
                || status == VerificationStatus.GUEST_NO_SHOW
                || status == VerificationStatus.BOTH_NO_SHOW;

        if (!isNoShow) {
            throw new DisputeException(ErrorCode.DISPUTE_NOT_NO_SHOW);
        }

        // 6. 장소 인증(GPS) 완료 검증
        // 요청자가 등록하면 등록자 GPS 진입 시각을, 신청자면 신청자 GPS 진입 시각을 본다.
        boolean isRequesterHost = userId.equals(authorId);
        boolean placeVerified = isRequesterHost
                ? meet.authorPlaceVerifiedAt() != null
                : meet.applicantPlaceVerifiedAt() != null;
        if (!placeVerified) {
            throw new DisputeException(ErrorCode.DISPUTE_PLACE_NOT_VERIFIED);
        }

        // 7. 중복 제출 검증
        if (disputeRepository.existsByMatchIdAndSubmitterId(matchId, userId)) {
            throw new DisputeException(ErrorCode.DISPUTE_ALREADY_SUBMITTED);
        }

        // 8. 24시간 제한 검증
        LocalDateTime decidedAt = meet.noShowDecidedAt();
        if (decidedAt == null || Duration.between(decidedAt, LocalDateTime.now()).toHours() >= 24L) {
                throw new DisputeException(ErrorCode.DISPUTE_DEADLINE_EXCEEDED);
        }

        // 9. 저장
        // evidenceUrl은 S3도입 전까지 null로 고정
        Dispute dispute = Dispute.builder()
                .matchId(matchId)
                .submitterId(userId)
                .disputeType(request.getDisputeType())
                .reason(request.getReason())
                // TODO: 추 후 S3 도입 이후에 변경 예정
                .evidenceUrl(null)
                .parentDisputeId(null)
                .build();
        Dispute saved = disputeRepository.save(dispute);

        // 11번 알림 - 관리자에게 이의제기 접수 알림 발송
        Long adminId = adminService.getAdminId();
        if (adminId != null) {
            notificationPublisher.sendDisputeSubmitted(adminId, saved.getId());
        }

        return CreateDisputeResponseDto.from(saved);
    }

    /**
     * 내 이의제기 상태 조회
     */
    @Override
    @Transactional(readOnly = true)
    public DisputeResponseDto getDispute(Long matchId, Long userId) {

        // 1. 매칭 존재 확인
        matchService.getMatchInfo(matchId);

        Dispute dispute = disputeRepository.findByMatchIdAndSubmitterId(matchId, userId)
                .orElseThrow(() -> new DisputeException(ErrorCode.DISPUTE_NOT_FOUND));

        // HOLD 상태일 때만 holdAt + 24시간, 아니면 null
        LocalDateTime holdDeadlineAt = dispute.getHoldAt()
                != null ? dispute.getHoldAt().plusHours(24) : null;

        return DisputeResponseDto.of(dispute.getId(),
                dispute.getMatchId(),
                dispute.getDisputeType(),
                dispute.getReason(),
                dispute.getStatus(),
                dispute.getAdminComment(),
                dispute.getCreatedAt(),
                dispute.getProcessedAt(),
                holdDeadlineAt
        );
    }

    // 어드민 이의제기 상세 조회용 - disputeId 단건 조회
    @Override
    public Dispute getDisputeById(Long disputeId) {
        return disputeRepository.findById(disputeId)
                .orElseThrow(() -> new DisputeException(ErrorCode.DISPUTE_NOT_FOUND));
    }

    @Override
    public Page<Dispute> getDisputesForAdmin(DisputeStatus status, Pageable pageable) {
        // status가 null이면 전체 조회, 있으면 해당 status만 필터링
        if (status == null) {
            return disputeRepository.findAll(pageable);
        }
        return disputeRepository.findAllByStatus(status, pageable);
    }

    // 관리자 - 노쇼 후보군 조회
    // matchId 목록으로 이의제기 존재 여부를 한 번에 조회 (N+1 방지)
    @Override
    public Set<Long> getMatchIdsWithDispute(List<Long> matchIds) {

        if (matchIds == null || matchIds.isEmpty()) {
            return Collections.emptySet();
        }

        // List -> Set 변환
        return new HashSet<>(disputeRepository.findMatchIdsByMatchIdIn(matchIds));
    }

    // 재이의제기 신청
    @Override
    @Transactional
    public CreateDisputeResponseDto reCreateDispute(Long matchId, Long userId, CreateDisputeRequestDto request) {

        // 매칭 존재 확인
        MatchInfoDto match = matchService.getMatchInfo(matchId);

        // 등록자 ID 조회
        PostInfoDto post = postService.getPostInfo(match.postId());
        Long authorId = post.authorId();

        // 당사자 검증 (등록자 또는 신청자인지)
        if (!match.isParticipant(userId, authorId)) {
            throw new DisputeException(ErrorCode.MATCH_NOT_PARTICIPANT);
        }

        // HOLD 상태인 원본 이의제기 조회
        // 없으면 -> HOLD 상태 이의제기가 없다는 뜻 -> 재이의제기 불가능
        Dispute parentDispute = disputeRepository.findHoldDisputeByMatchIdAndSubmitterId(matchId, userId)
                .orElseThrow(() -> new DisputeException(ErrorCode.DISPUTE_HOLD_NOT_FOUND));

        // HOLD 상태인지 재 확인 (동시성 이슈 대비)
        if (parentDispute.getStatus() != DisputeStatus.HOLD) {
            throw new DisputeException(ErrorCode.DISPUTE_NOT_RESUBMITTABLE);
        }

        // 같은 disputeType이 맞는지 검증
        if (parentDispute.getDisputeType() != request.getDisputeType()) {
            throw new DisputeException(ErrorCode.DISPUTE_TYPE_MISMATCH);
        }

        // HOLD 판정 후 24시간 이내인지 검증
        if (!parentDispute.isWithinHoldResubmitDeadline()) {
            throw new DisputeException(ErrorCode.DISPUTE_HOLD_DEADLINE_EXCEEDED);
        }

        // 재이의제기 중복 제출 방지
        if (disputeRepository.existsByMatchIdAndSubmitterIdAndParentDisputeId(matchId, userId, parentDispute.getId())) {
            throw new DisputeException(ErrorCode.DISPUTE_ALREADY_SUBMITTED);
        }

        // 이의제기 저장
        Dispute reDispute = Dispute.builder()
                .matchId(matchId)
                .submitterId(userId)
                .disputeType(request.getDisputeType())
                .reason(request.getReason())
                // TODO: S3 도입 후 evidenceUrl 처리 추가
                .evidenceUrl(null)
                .parentDisputeId(parentDispute.getId()) // 원본 이의제기 ID 연결
                .build();
        Dispute savedReDispute = disputeRepository.save(reDispute);

        // 11번 알림 - 관리자에게 재이의제기 접수 알림 발송
        Long adminId = adminService.getAdminId();
        if (adminId != null) {
            notificationPublisher.sendDisputeSubmitted(adminId, savedReDispute.getId());
        }

        return CreateDisputeResponseDto.from(savedReDispute);
    }

    // 노쇼 확정에서 사용 할 이의제기 상태 조회
    @Override
    public Set<Long> getMatchIdsWithActiveDispute(List<Long> matchIds) {

        if (matchIds == null || matchIds.isEmpty()) {
            return Collections.emptySet();
        }

        // SUBMITTED / UNDER_REVIEW / HOLD = 관리자가 아직 처리 중인 상태
        // 이 상태들이 존재하면 배치가 건드리면 안 됨
        List<DisputeStatus> activeStatuses = List.of(
                DisputeStatus.SUBMITTED,
                DisputeStatus.UNDER_REVIEW,
                DisputeStatus.HOLD
        );

        return new HashSet<>(
                disputeRepository.findMatchIdsByMatchIdInAndStatusIn(matchIds, activeStatuses)
        );
    }

}
