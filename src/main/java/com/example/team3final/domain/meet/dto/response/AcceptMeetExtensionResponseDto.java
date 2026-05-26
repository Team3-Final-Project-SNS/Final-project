package com.example.team3final.domain.meet.dto.response;

import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.enums.ExtensionStatus;

import java.time.LocalDateTime;

public record AcceptMeetExtensionResponseDto(

        Long matchId,
        ExtensionStatus extensionStatus,
        LocalDateTime originalMeetAt,   // post.meetAt (연장 전 원래 시각)
        LocalDateTime extendedMeetAt,   // meetVerification.extendedMeetAt (meetAt + 15분)
        boolean isExtended,
        LocalDateTime extendedAt        // 수락 처리 시각 (LocalDateTime.now())
) {
    public static AcceptMeetExtensionResponseDto of(
            MeetVerification meetVerification,
            LocalDateTime originalMeetAt) {
        return new AcceptMeetExtensionResponseDto(
                meetVerification.getMatchId(),
                meetVerification.getExtensionStatus(),
                originalMeetAt,
                meetVerification.getExtendedMeetAt(), // acceptExtension() 호출 후 저장된 값
                meetVerification.isExtended(),
                LocalDateTime.now()                   // 수락 시각은 DB 저장 없이 계산
        );
    }
}
