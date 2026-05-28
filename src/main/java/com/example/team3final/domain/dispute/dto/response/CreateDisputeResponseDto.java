package com.example.team3final.domain.dispute.dto.response;

import com.example.team3final.domain.dispute.entity.Dispute;
import com.example.team3final.domain.dispute.enums.DisputeStatus;

import java.time.LocalDateTime;

public record CreateDisputeResponseDto(
        Long disputeId,
        Long matchId,
        DisputeStatus status,
        LocalDateTime submittedAt
) {
    public static CreateDisputeResponseDto from(Dispute dispute) {
        return new CreateDisputeResponseDto(
                dispute.getId(),
                dispute.getMatchId(),
                dispute.getStatus(),
                dispute.getCreatedAt()
        );
    }
}
