package com.example.team3final.domain.meet.dto.response;

import com.example.team3final.domain.meet.entity.MeetVerification;

import java.time.LocalDateTime;

public record QrResponseDto (

        Long matchId,
        String qrToken,
        LocalDateTime expiresAt
) {
    public static QrResponseDto of(Long matchId, MeetVerification meetVerification) {
        return new QrResponseDto(
                matchId,
                meetVerification.getQrToken(),
                meetVerification.getQrExpiresAt()
        );
    }
}
