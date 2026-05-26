package com.example.team3final.domain.meet.dto.response;

import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.enums.ExtensionStatus;

import java.time.LocalDateTime;

public record RejectMeetExtensionResponseDto(

        Long matchId,
        ExtensionStatus extensionStatus,
        LocalDateTime rejectedAt
) {
    public static RejectMeetExtensionResponseDto from(MeetVerification meetVerification) {
        return new RejectMeetExtensionResponseDto(
                meetVerification.getMatchId(),
                meetVerification.getExtensionStatus(),
                LocalDateTime.now()
        );
    }
}
