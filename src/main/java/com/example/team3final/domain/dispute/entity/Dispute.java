package com.example.team3final.domain.dispute.entity;

import com.example.team3final.domain.dispute.enums.DisputeStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "disputes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Dispute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 분쟁 대상 매칭 ID
    @Column(name = "match_id", nullable = false)
    private Long matchId;

    // 이의제기를 제출한 유저 ID
    @Column(name = "submitter_id", nullable = false)
    private Long submitterId;

    // 이의제기 사유 - text 타입, 최대 1000자
    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

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

    // 이의제기 제출 시각
    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    // 관리자 처리 완료 시각
    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Builder
    private Dispute(Long matchId, Long submitterId, String reason) {
        this.matchId = matchId;
        this.submitterId = submitterId;
        this.reason = reason;
        this.status = DisputeStatus.SUBMITTED;  // 이의제기는 항상 SUBMITTED 로 시작
        this.submittedAt = LocalDateTime.now(); // 제출 시각 = 생성 시각
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

    // ===== 조회(검증 보조) 메서드 =====

    /**
     * 이 이의제기를 제출한 본인인지 확인 — getDispute 권한 검증에 사용.
     */
    public boolean isSubmitter(Long userId) {
        return this.submitterId.equals(userId);
    }
}
