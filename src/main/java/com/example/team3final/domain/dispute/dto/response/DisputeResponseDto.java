package com.example.team3final.domain.dispute.dto.response;

import com.example.team3final.domain.dispute.entity.Dispute;
import com.example.team3final.domain.dispute.enums.DisputeStatus;

import java.time.LocalDateTime;

public record DisputeResponseDto(
        Long disputeId,
        Long matchId,
        String reason,
        DisputeStatus status,
        String adminComment,        // 아직 처리 전이면 null
        LocalDateTime submittedAt,
        LocalDateTime processedAt   // 아직 처리 전이면 null
) {
    public static DisputeResponseDto from(Dispute dispute) {
        return new DisputeResponseDto(
                dispute.getId(),
                dispute.getMatchId(),
                dispute.getReason(),
                dispute.getStatus(),
                dispute.getAdminComment(),
                dispute.getSubmittedAt(),
                dispute.getProcessedAt()
        );
    }
}
