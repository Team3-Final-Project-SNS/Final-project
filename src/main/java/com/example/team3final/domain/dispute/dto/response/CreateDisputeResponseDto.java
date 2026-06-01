package com.example.team3final.domain.dispute.dto.response;

import com.example.team3final.domain.dispute.entity.Dispute;
import com.example.team3final.domain.dispute.enums.DisputeStatus;
import com.example.team3final.domain.dispute.enums.DisputeType;

import java.time.LocalDateTime;

public record CreateDisputeResponseDto(
        Long disputeId,
        Long matchId,
        DisputeType disputeType, // 제출한 이의제기 타입
        DisputeStatus status,    // 항상 SUBMITTED
        LocalDateTime submittedAt
) {
    public static CreateDisputeResponseDto from(Dispute dispute) {
        return new CreateDisputeResponseDto(
                dispute.getId(),
                dispute.getMatchId(),
                dispute.getDisputeType(),
                dispute.getStatus(),
                dispute.getCreatedAt()
        );
    }
}
