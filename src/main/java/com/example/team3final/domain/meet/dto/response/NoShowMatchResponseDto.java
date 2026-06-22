package com.example.team3final.domain.meet.dto.response;

import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.enums.VerificationStatus;

import java.time.LocalDateTime;

public record NoShowMatchResponseDto(
        Long matchId,
        VerificationStatus verificationStatus,
        LocalDateTime noShowDecidedAt
) {
    public static NoShowMatchResponseDto from(MeetVerification mv) {
        return new NoShowMatchResponseDto(
                mv.getMatchId(),
                mv.getStatus(),
                mv.getNoShowDecidedAt()
        );
    }
}