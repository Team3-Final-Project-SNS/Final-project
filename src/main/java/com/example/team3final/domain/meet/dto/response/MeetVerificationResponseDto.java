package com.example.team3final.domain.meet.dto.response;

import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.enums.VerificationStatus;

import java.time.LocalDateTime;

public record MeetVerificationResponseDto (

        Long matchId,
        VerificationStatus verificationStatus,
        LocalDateTime authorPlaceVerifiedAt,    // 등록자 GPS 인증 시각 (null 가능)
        LocalDateTime applicantPlaceVerifiedAt, // 신청자 GPS 인증 시각 (null 가능)
        boolean qrIssuedToAuthor,               // QR 토큰 발급 여부
        LocalDateTime qrExpiresAt,              // QR 만료 시각 (null = 미발급)
        LocalDateTime completedAt,               // 만남 인증 완료 시각 (null = 미완료)
        LocalDateTime noShowDecidedAt
) {
    public static MeetVerificationResponseDto of(Long matchId, MeetVerification meetVerification) {
        return new MeetVerificationResponseDto (
                matchId,
                meetVerification.getStatus(),
                meetVerification.getAuthorPlaceVerifiedAt(),
                meetVerification.getApplicantPlaceVerifiedAt(),
                meetVerification.getQrToken() != null, // QR토큰이 null이 아니면 발급
                meetVerification.getQrExpiresAt(),
                meetVerification.getCompletedAt(),
                meetVerification.getNoShowDecidedAt()
        );
    }
}
