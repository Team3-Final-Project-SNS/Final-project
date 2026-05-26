package com.example.team3final.domain.meet.dto.response;

import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.enums.ExtensionStatus;

import java.time.LocalDateTime;

public record GetMeetExtensionResponseDto(

        Long matchId,
        ExtensionStatus extensionStatus,
        Long requesterId,
        String requesterNickname,
        boolean isMyRequest,                // 조회한 사람이 요청자인지 여부
        LocalDateTime originalMeetAt,
        LocalDateTime expectedMeetAt,       // originalMeetAt + 15분 (REQUESTED 상태일 때만 의미 있음)
        LocalDateTime requestedAt,
        LocalDateTime expiresAt             // requestedAt + 5분
) {
    public static GetMeetExtensionResponseDto of(
            MeetVerification meetVerification,
            String requesterNickname,
            LocalDateTime originalMeetAt,
            Long currentUserId              // 조회 요청한 유저 ID
    ) {
        return new GetMeetExtensionResponseDto(
                meetVerification.getMatchId(),
                meetVerification.getExtensionStatus(),
                meetVerification.getExtensionRequesterId(),
                requesterNickname,
                meetVerification.getExtensionRequesterId() != null
                        && currentUserId.equals(meetVerification.getExtensionRequesterId()),
                originalMeetAt,
                originalMeetAt.plusMinutes(15),
                meetVerification.getExtensionRequestedAt(),
                meetVerification.getExtensionRequestedAt() != null
                 ? meetVerification.getExtensionRequestedAt().plusMinutes(5) : null // NONE 상태면 요청 시각 자체가 null
        );
    }
}
