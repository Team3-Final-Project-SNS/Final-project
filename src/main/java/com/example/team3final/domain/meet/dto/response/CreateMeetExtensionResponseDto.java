package com.example.team3final.domain.meet.dto.response;

import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.enums.ExtensionStatus;

import java.time.LocalDateTime;

public record CreateMeetExtensionResponseDto(

        Long matchId,
        ExtensionStatus extensionStatus,
        Long requesterId,
        String requesterNickname,
        LocalDateTime originalMeetAt,       // post.meetAt
        LocalDateTime expectedMeetAt,       // originalMeetAt + 15분
        LocalDateTime requestedAt,          // 요청 시각
        LocalDateTime expiresAt             // requestedAt + 5분
) {
    public static CreateMeetExtensionResponseDto of(
            MeetVerification meetVerification,
            String requesterNickname,
            LocalDateTime originalMeetAt) {
        return new CreateMeetExtensionResponseDto(
                meetVerification.getMatchId(),
                meetVerification.getExtensionStatus(),
                meetVerification.getExtensionRequesterId(),
                requesterNickname,
                originalMeetAt,
                originalMeetAt.plusMinutes(15),                           // 예상 연장 시각
                meetVerification.getExtensionRequestedAt(),
                meetVerification.getExtensionRequestedAt().plusMinutes(5) // 만료 시각
        );
    }
}
