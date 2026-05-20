package com.example.team3final.domain.meet.dto.response;

import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.enums.VerificationStatus;

import java.time.LocalDateTime;

public record PlaceVerificationResponseDto (

        Long matchId,
        VerificationStatus verificationStatus,  // 현재 인증 상태
        double distanceMeters,                  // 약속 장소까지의 거리(m)
        LocalDateTime authorPlaceVerifiedAt,    // 등록자 GPS 인증 시각 (null 가능)
        LocalDateTime applicantPlaceVerifiedAt, // 신청자 GPS 인증 시각 (null 가능)
        boolean bothVerified                    // 양측 모두 인증 완료 여부
) {
    public static PlaceVerificationResponseDto of(
            MeetVerification meetVerification,
            double distanceMeters,
            boolean bothVerified) {
        return new PlaceVerificationResponseDto(
                meetVerification.getMatchId(),
                meetVerification.getStatus(),
                distanceMeters,
                meetVerification.getAuthorPlaceVerifiedAt(),
                meetVerification.getApplicantPlaceVerifiedAt(),
                bothVerified
        );
    }
}
