package com.example.team3final.domain.meet.dto.response;

import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.enums.VerificationStatus;

import java.time.LocalDateTime;

public record QrScanResponseDto(

        Long matchId,
        VerificationStatus verificationStatus,
        MatchStatus matchStatus,
        LocalDateTime completedAt,
        // TODO: point 환불 로직과 연결
        int refundedPoint
) {
    public static QrScanResponseDto of(
            Long matchId,
            MeetVerification meetVerification,
            MatchStatus matchStatus,
            int refundedPoint) {
        return new QrScanResponseDto(
                matchId,
                meetVerification.getStatus(),
                matchStatus,
                meetVerification.getCompletedAt(),
                refundedPoint
        );
    }
}
