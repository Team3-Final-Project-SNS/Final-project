package com.example.team3final.domain.report.entity;

import com.example.team3final.common.entity.BaseTimeEntity;
import com.example.team3final.common.entity.SoftDeleteEntity;
import com.example.team3final.domain.report.enums.ReportReason;
import com.example.team3final.domain.report.enums.ReportStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 신고 사유: SPAM / OBSCENE / FRAUD / ABUSE / OTHER
 * 처리 상태: PENDING → ACCEPTED / REJECTED / WITHDRAWN
 */

@Getter
@Entity
@Table(name = "reports")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // 신고한 유저 ID
    @Column(name = "reporter_id", nullable = false, updatable = false)
    private Long reporterId;

    // 신고 대상 엔티티 ID
    @Column(name = "target_id", nullable = false, updatable = false)
    private Long targetId;

    // 신고 사유
    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, updatable = false, length = 20)
    private ReportReason reason;

    // 신고 상세 내용 (선택)
    @Column(name = "detail", length = 500)
    private String detail;

    // 관리자 처리 상태
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReportStatus status;

    // 신고 채택 포상 지급 여부
    @Column(name = "is_rewarded", nullable = false)
    private boolean isRewarded;

    // 처리한 관리자 ID
    @Column(name = "admin_id")
    private Long adminId;

    // 처리 완료 시각
    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    // 신고 취소 시각
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Builder
    private Report(Long reporterId, Long targetId,
                   ReportReason reason, String detail) {
        this.reporterId = reporterId;
        this.targetId = targetId;
        this.reason = reason;
        this.detail = detail;
        this.status = ReportStatus.PENDING; // 생성 시 항상 대기 상태
        this.isRewarded = false; // 생성 시 포상 미지급
    }

    // ==================== 도메인 메서드 ====================

    // 신고 취소 (WITHDRAWN)
    public void withdraw() {
        this.status = ReportStatus.WITHDRAWN;
        this.cancelledAt = LocalDateTime.now();
    }

    // 신고 채택 (ACCEPTED) - 관리자 처리
    public void accept(Long adminId) {
        this.status = ReportStatus.ACCEPTED;
        this.adminId = adminId;
        this.processedAt = LocalDateTime.now();
    }

    // 신고 기각 (REJECTED) - 관리자 처리
    public void reject(Long adminId) {
        this.status = ReportStatus.REJECTED;
        this.adminId = adminId;
        this.processedAt = LocalDateTime.now();
    }

    // 포상 지급 완료 처리
    public void markRewarded() {
        this.isRewarded = true;
    }

    // 취소 가능 여부 확인 (PENDING 상태만 가능)
    public boolean isWithdrawable() {
        return this.status == ReportStatus.PENDING;
    }

    // 이미 처리된 신고인지 확인
    public boolean isProcessed() {
        return this.status == ReportStatus.ACCEPTED
                || this.status == ReportStatus.REJECTED;
    }
}