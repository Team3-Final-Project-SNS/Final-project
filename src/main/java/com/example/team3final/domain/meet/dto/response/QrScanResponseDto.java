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
        int refundedPoint   // 신청자 예치 포인트 전액 환불 금액 (QR 스캔 성공 시 100% 반환)
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
