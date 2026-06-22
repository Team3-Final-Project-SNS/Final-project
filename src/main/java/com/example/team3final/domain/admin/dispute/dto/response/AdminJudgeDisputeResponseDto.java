package com.example.team3final.domain.admin.dispute.dto.response;

import com.example.team3final.domain.dispute.entity.Dispute;
import com.example.team3final.domain.dispute.enums.DisputeStatus;

import java.time.LocalDateTime;

public record AdminJudgeDisputeResponseDto (

        Long disputeId,              // 이의제기 ID
        Long matchId,                // 매칭 ID
        DisputeStatus status,        // 최종 판정 결과
        String adminComment,         // 관리자 코멘트
        int refundedPoint,           // 실제 반환된 포인트 (0이면 REJECTED)
        LocalDateTime processedAt    // 판정 완료 시각
) {
    public static AdminJudgeDisputeResponseDto of(Dispute dispute, int refundedPoint) {
        return new AdminJudgeDisputeResponseDto(
                dispute.getId(),
                dispute.getMatchId(),
                dispute.getStatus(),
                dispute.getAdminComment(),
                refundedPoint,
                dispute.getProcessedAt()
        );
    }
}
