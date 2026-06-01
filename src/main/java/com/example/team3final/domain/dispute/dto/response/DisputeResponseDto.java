package com.example.team3final.domain.dispute.dto.response;

import com.example.team3final.domain.dispute.enums.DisputeStatus;
import com.example.team3final.domain.dispute.enums.DisputeType;

import java.time.LocalDateTime;

public record DisputeResponseDto(

        Long disputeId,             // 이의제기 ID
        Long matchId,               // 매칭 ID
        DisputeType disputeType,    // 내가 신청한 이의제기 타입 (GPS_ERROR 등)
        String reason,              // 이의제기 사유 상세 텍스트
        DisputeStatus status,       // 현재 처리 상태 (SUBMITTED / UNDER_REVIEW / HOLD 등)
        String adminComment,        // 관리자 코멘트 — 판정 전이면 null
        LocalDateTime submittedAt,  // 이의제기 제출 시각 (BaseTimeEntity.createdAt)
        LocalDateTime processedAt,  // 판정 완료 시각 — 처리 전이면 null
        LocalDateTime holdDeadlineAt // HOLD 상태일 때 재신청 마감 시각 (holdAt + 24h)
                                     // HOLD 아니면 null — 프론트 카운트다운 표시용
) {
    // from() 제거 — 서비스에서 of()로 직접 생성
    public static DisputeResponseDto of(
            Long disputeId,
            Long matchId,
            DisputeType disputeType,
            String reason,
            DisputeStatus status,
            String adminComment,
            LocalDateTime submittedAt,
            LocalDateTime processedAt,
            LocalDateTime holdDeadlineAt
    ) {
        return new DisputeResponseDto(
                disputeId,
                matchId,
                disputeType,
                reason,
                status,
                adminComment,
                submittedAt,
                processedAt,
                holdDeadlineAt
        );
    }
}
