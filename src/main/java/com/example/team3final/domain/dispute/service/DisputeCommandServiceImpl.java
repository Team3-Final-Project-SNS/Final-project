package com.example.team3final.domain.dispute.service;

import com.example.team3final.common.exception.DisputeException;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.domain.admin.service.AdminService;
import com.example.team3final.domain.dispute.dto.request.CreateDisputeRequestDto;
import com.example.team3final.domain.dispute.dto.response.CreateDisputeResponseDto;
import com.example.team3final.domain.dispute.dto.response.DisputeResponseDto;
import com.example.team3final.domain.dispute.dto.response.MyDisputeResponseDto;
import com.example.team3final.domain.dispute.entity.Dispute;
import com.example.team3final.domain.dispute.enums.DisputeStatus;
import com.example.team3final.domain.dispute.repository.DisputeRepository;
import com.example.team3final.domain.dispute.util.DisputeRedisZSetKeys;
import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.service.MatchInternalService;
import com.example.team3final.domain.match.service.MatchNoShowService;
import com.example.team3final.domain.meet.dto.response.MeetVerificationResponseDto;
import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.enums.VerificationStatus;
import com.example.team3final.domain.meet.service.MeetVerificationInternalService;
import com.example.team3final.domain.meet.service.MeetVerificationQueryService;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.dto.response.PostInfoDto;
import com.example.team3final.domain.post.service.PostInternalService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
// Dispute 도메인의 사용자 요청 기반 이의제기 생성/재신청/조회 기능을 담당하는 서비스
public class DisputeCommandServiceImpl implements DisputeCommandService {

    private final DisputeRepository disputeRepository;
    private final MatchInternalService matchInternalService;
    private final MatchNoShowService matchNoShowService;
    private final PostInternalService postInternalService;
    private final MeetVerificationQueryService meetVerificationQueryService;
    private final MeetVerificationInternalService meetVerificationInternalService;
    private final AdminService adminService;
    private final NotificationPublisher notificationPublisher;
    private final StringRedisTemplate redisTemplate;

    // 이의제기 제출
    @Override
    public CreateDisputeResponseDto createDispute(Long matchId, Long userId, CreateDisputeRequestDto request) {

        // 1. 매칭 존재 확인
        MatchInfoDto match = matchInternalService.getMatchInfo(matchId);

        // 2. 등록자 조회
        PostInfoDto post = postInternalService.getPostInfo(match.postId());
        Long authorId = post.authorId();

        // 3. 당사자 검증
        if (!match.isParticipant(userId, authorId)) {
            throw new DisputeException(ErrorCode.MATCH_NOT_PARTICIPANT);
        }

        // 4. 만남 인증 정보 조회
        MeetVerificationResponseDto meet = meetVerificationQueryService.getMeetVerification(userId, matchId);

        // 5. 현재 인증 상태 확인
        VerificationStatus status = meet.verificationStatus();
        boolean isNoShow = status == VerificationStatus.HOST_NO_SHOW
                || status == VerificationStatus.GUEST_NO_SHOW
                || status == VerificationStatus.BOTH_NO_SHOW;

        // 6. 중복 제출 검증
        if (disputeRepository.existsByMatchIdAndSubmitterId(matchId, userId)) {
            throw new DisputeException(ErrorCode.DISPUTE_ALREADY_SUBMITTED);
        }

        // 7. 노쇼 예정 상태 검증
        if (!isNoShow) {
            throw new DisputeException(ErrorCode.DISPUTE_NOT_NO_SHOW);
        }

        // 8. 24시간 제한 검증
        LocalDateTime decidedAt = meet.noShowDecidedAt();
        if (decidedAt == null || Duration.between(decidedAt, LocalDateTime.now()).toHours() >= 24L) {
            throw new DisputeException(ErrorCode.DISPUTE_DEADLINE_EXCEEDED);
        }

        // 9. 저장
        // evidenceUrl은 S3 도입 전까지 null로 고정
        Dispute dispute = Dispute.builder()
                .matchId(matchId)
                .submitterId(userId)
                .disputeType(request.getDisputeType())
                .reason(request.getReason())
                // TODO: 추후 S3 도입 이후에 변경 예정
                .evidenceUrl(null)
                .parentDisputeId(null)
                .build();
        Dispute saved = disputeRepository.save(dispute);

        // 이의제기 접수 시 노쇼 예정 상태를 보존하고 관리자 검토 상태로 전환
        MeetVerification meetVerification = meetVerificationInternalService.getByMatchId(matchId);
        meetVerification.markDispute();
        matchNoShowService.markDisputed(matchId);

        // 이의제기 접수 알림 - 활성 관리자 모두에게
        adminService.getActiveAdminIds().forEach(
                adminId -> notificationPublisher.sendDisputeSubmitted(adminId, saved.getId())
        );

        return CreateDisputeResponseDto.from(saved);
    }

    // 재이의제기 제출
    @Override
    public CreateDisputeResponseDto reCreateDispute(Long matchId, Long userId, CreateDisputeRequestDto request) {

        // 매칭 존재 확인
        MatchInfoDto match = matchInternalService.getMatchInfo(matchId);

        // 등록자 ID 조회
        PostInfoDto post = postInternalService.getPostInfo(match.postId());
        Long authorId = post.authorId();

        // 당사자 검증 (등록자 또는 신청자인지)
        if (!match.isParticipant(userId, authorId)) {
            throw new DisputeException(ErrorCode.MATCH_NOT_PARTICIPANT);
        }

        // HOLD 상태인 원본 이의제기 조회
        // 없으면 → HOLD 상태 이의제기가 없다는 뜻 → 재이의제기 불가능
        Dispute parentDispute = disputeRepository.findHoldDisputeByMatchIdAndSubmitterId(matchId, userId)
                .orElseThrow(() -> new DisputeException(ErrorCode.DISPUTE_HOLD_NOT_FOUND));

        // HOLD 상태인지 재확인 (동시성 이슈 대비)
        if (parentDispute.getStatus() != DisputeStatus.HOLD) {
            throw new DisputeException(ErrorCode.DISPUTE_NOT_RESUBMITTABLE);
        }

        // 같은 disputeType인지 검증
        if (parentDispute.getDisputeType() != request.getDisputeType()) {
            throw new DisputeException(ErrorCode.DISPUTE_TYPE_MISMATCH);
        }

        // HOLD 판정 후 24시간 이내인지 검증
        if (!parentDispute.isWithinHoldResubmitDeadline()) {
            throw new DisputeException(ErrorCode.DISPUTE_HOLD_DEADLINE_EXCEEDED);
        }

        // UNIQUE(match_id, submitter_id)를 유지하므로 새 레코드를 만들지 않고
        // 기존 HOLD 이의제기 row를 SUBMITTED 상태로 재전환한다.
        parentDispute.resubmit(
                request.getDisputeType(),
                request.getReason(),
                null
        );

        // 재이의제기 완료 → 마감 임박 알림 예약 취소
        // 원본 이의제기 ID를 ZSet에서 제거해 중복 알림 방지
        redisTemplate.opsForZSet().remove(
                DisputeRedisZSetKeys.DEADLINE_REMINDER,
                String.valueOf(parentDispute.getId())
        );

        // 이의제기 접수 알림 - 활성 관리자 모두에게
        adminService.getActiveAdminIds().forEach(
                adminId -> notificationPublisher.sendDisputeSubmitted(adminId, parentDispute.getId())
        );

        return CreateDisputeResponseDto.from(parentDispute);
    }

    // 특정 매칭에 대해 내가 제출한 이의제기 상세 조회
    @Override
    @Transactional(readOnly = true)
    // readOnly = true: 이 메서드는 DB를 읽기만 하므로 JPA 더티 체킹(변경 감지)을 비활성화
    // → 불필요한 스냅샷 비교를 생략해 성능 향상
    public DisputeResponseDto getDispute(Long matchId, Long userId) {

        // 매칭 존재 확인
        matchInternalService.getMatchInfo(matchId);

        Dispute dispute = disputeRepository.findByMatchIdAndSubmitterId(matchId, userId)
                .orElseThrow(() -> new DisputeException(ErrorCode.DISPUTE_NOT_FOUND));

        // HOLD 상태일 때만 holdAt + 24시간, 아니면 null
        // 프론트에서 재이의제기 마감 카운트다운 표시에 사용
        LocalDateTime holdDeadlineAt = dispute.getHoldAt() != null
                ? dispute.getHoldAt().plusHours(24)
                : null;

        return DisputeResponseDto.of(
                dispute.getId(),
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

    // 내가 제출한 이의제기 전체 목록 조회
    @Override
    @Transactional(readOnly = true)
    // readOnly = true: 조회 전용이므로 더티 체킹 비활성화
    public List<MyDisputeResponseDto> getMyDisputes(Long userId) {

        // submitterId = userId 조건으로 내 이의제기 전부 조회, 최신순 정렬
        // 결과가 없으면 빈 List 반환 (null 반환 X → NullPointerException 방지)
        return disputeRepository
                .findAllBySubmitterIdOrderByCreatedAtDesc(userId) // Repository 쿼리 호출
                .stream()                                          // List → Stream 변환
                .map(MyDisputeResponseDto::from)                  // 각 엔티티 → DTO 변환
                .toList();                                         // Stream → List 변환
    }
}
