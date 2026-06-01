package com.example.team3final.domain.dispute.entity;

import com.example.team3final.common.entity.BaseTimeEntity;
import com.example.team3final.domain.dispute.enums.DisputeStatus;
import com.example.team3final.domain.dispute.enums.DisputeType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "disputes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Dispute extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 분쟁 대상 매칭 ID
    @Column(name = "match_id", nullable = false)
    private Long matchId;

    // 이의제기를 제출한 유저 ID
    @Column(name = "submitter_id", nullable = false)
    private Long submitterId;

    // 이의제기 사유 타입
    @Enumerated(EnumType.STRING)
    @Column(name = "dispute_type", nullable = false, length = 30)
    private DisputeType disputeType;

    // 이의제기 사유 - text 타입, 최대 1000자
    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    // 증빙자료 S3 URL
    // 컬럼만 미리 설계, 실제 업로드는 S3 도입 후 추가 구현
    @Column(name = "evidence_url", length = 500)
    private String evidenceUrl;

    // 처리 상태 - 반드시 STRING 으로 저장
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DisputeStatus status;

    // 검토한 관리자 ID
    @Column(name = "admin_id")
    private Long adminId;

    // 관리자 최종 판정 사유
    @Column(name = "admin_comment", columnDefinition = "TEXT")
    private String adminComment;

    // 관리자 처리 완료 시각
    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    // HOLD 판정 시각
    @Column(name = "hold_at")
    private LocalDateTime holdAt;

    // 재이의제기인 경우 원본 이의제기 ID, 최초 이의제기는 null
    @Column(name = "parent_dispute_id")
    private Long parentDisputeId;

    @Builder
    private Dispute(Long matchId, Long submitterId, DisputeType disputeType,
                    String reason, String evidenceUrl, Long parentDisputeId) {
        this.matchId = matchId;
        this.submitterId = submitterId;
        this.disputeType = disputeType;
        this.reason = reason;
        this.evidenceUrl = evidenceUrl;
        this.parentDisputeId = parentDisputeId;
        this.status = DisputeStatus.SUBMITTED; // 항상 SUBMITTED로 시작
    }

    // ===== 비즈니스 메서드 =====

    /**
     * 관리자가 검토를 시작 → UNDER_REVIEW.
     * (admin 도메인에서 호출. 이번 범위는 아니지만 상태 머신 완성도를 위해 둠)
     */
    public void startReview(Long adminId) {
        // SUBMITTED 상태에서만 검토 시작 가능
        if (this.status != DisputeStatus.SUBMITTED) {
            throw new IllegalStateException("검토 시작은 SUBMITTED 상태에서만 가능합니다.");
        }
        this.status = DisputeStatus.UNDER_REVIEW;
        this.adminId = adminId;
    }

    public void process(DisputeStatus result, Long adminId, String adminComment) {

        // 종결 상태(ACCEPTED/PARTIALLY_ACCEPTED/REJECTED)만 결과로 허용
        if (!result.isClosed()) {
            throw new IllegalArgumentException("처리 결과는 종결 상태여야 합니다: " + result);
        }

        // 이미 종결된 건은 다시 처리 불가
        if (this.status.isClosed()) {
            throw new IllegalStateException("이미 처리된 이의제기입니다.");
        }
        this.status = result;
        this.adminId = adminId;
        this.adminComment = adminComment;
        this.processedAt = LocalDateTime.now();
    }

    // holdAt 기록,
    // 재의이제기 24시간 카운팅 시작
    public void hold(Long adminId, String adminComment) {
        if (this.status != DisputeStatus.UNDER_REVIEW) {
            throw new IllegalStateException("보류는 UNDER_REVIEW 상태에서만 가능합니다.");
        }
        this.status = DisputeStatus.HOLD;
        this.adminId = adminId;
        this.adminComment = adminComment;
        this.holdAt = LocalDateTime.now();
        this.processedAt = LocalDateTime.now();
    }

    // 관리자 강제 상태 변경 (오판정 정정용)
    public void forceChangeStatus(DisputeStatus newStatus, Long adminId, String adminComment) {

        // HOLD로 강제 전환 시 holdAt 갱신
        if (newStatus == DisputeStatus.HOLD) {
            this.holdAt = LocalDateTime.now();
        }

        // HOLD 해제 시 holdAt 초기화
        if (this.status == DisputeStatus.HOLD && newStatus != DisputeStatus.HOLD) {
            this.holdAt = null;
        }
        this.status = newStatus;
        this.adminId = adminId;
        this.adminComment = adminComment;
        this.processedAt = LocalDateTime.now();
    }

    // ===== 조회(검증 보조) 메서드 =====

    /**
     * 이 이의제기를 제출한 본인인지 확인 — getDispute 권한 검증에 사용.
     */
    public boolean isSubmitter(Long userId) {
        return this.submitterId.equals(userId);
    }

    // HOLD 판정 후 24시간 이내인지 확인
    public boolean isWithinHoldResubmitDeadline() {
        if (this.holdAt == null) return false;
        return Duration.between(this.holdAt, LocalDateTime.now()).toHours() < 24L;
    }
}
