package com.example.team3final.domain.admin.dispute.dto.response;

import com.example.team3final.domain.dispute.enums.DisputeStatus;

import java.time.LocalDateTime;

public record GetAdminDisputesResponseDto (

        Long disputeId,
        Long matchId,
        String applicantNickname,   // submitterId → User.nickname 변환값
        String reason,              // 이의제기 사유
        DisputeStatus status,       // 이의제기 상태
        LocalDateTime submittedAt   // 이의제기 제출 시각
) {
    public static GetAdminDisputesResponseDto of(
            Long disputeId,
            Long matchId,
            String applicantNickname,
            String reason,
            DisputeStatus status,
            LocalDateTime submittedAt
    ) {
        return new GetAdminDisputesResponseDto(
                disputeId,
                matchId,
                applicantNickname,
                reason,
                status,
                submittedAt
        );
    }
}
