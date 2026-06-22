package com.example.team3final.domain.meet.dto.response;

import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.enums.VerificationStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record MeetVerificationResponseDto (

        Long matchId,
        VerificationStatus verificationStatus,
        String authorNickname,                         // 등록자 닉네임
        LocalDateTime authorPlaceVerifiedAt,           // 등록자 GPS 인증 시각 (null 가능)
        List<ParticipantVerificationDto> participants, // 신청자별 인증 현황 목록
        boolean qrIssuedToAuthor,                      // QR 토큰 발급 여부
        LocalDateTime qrExpiresAt,                     // QR 만료 시각 (null = 미발급)
        LocalDateTime completedAt,                     // 만남 인증 완료 시각 (null = 미완료)
        LocalDateTime noShowDecidedAt
) {

    public record ParticipantVerificationDto(
            Long matchId,                    // 이 신청자의 matchId
            String nickname,                 // 신청자 닉네임
            boolean verified,                // 장소 인증 완료 여부
            LocalDateTime verifiedAt,        // 인증 시각 (null = 미인증)
            VerificationStatus verificationStatus,
            boolean meetVerified,
            LocalDateTime completedAt
    ) {}

    public static MeetVerificationResponseDto of(
            Long matchId,
            MeetVerification meetVerification,
            String authorNickname,
            List<MeetVerification> siblingMvList,
            Map<Long, ParticipantInfo> applicantInfoMap) {

        // MeetVerification 목록을 순회하며 신청자별 인증 현황 조립
        List<ParticipantVerificationDto> participants = siblingMvList.stream()
                .map(mv -> {
                    // 이 MeetVerification의 신청자 정보 조회
                    ParticipantInfo info = applicantInfoMap.get(mv.getMatchId());
                    String nickname = info != null ? info.nickname() : "알 수 없음";
                    boolean verified = mv.isApplicantPlaceVerified();
                    LocalDateTime verifiedAt = mv.getApplicantPlaceVerifiedAt();
                    boolean meetVerified = mv.getStatus() == VerificationStatus.DONE;
                    return new ParticipantVerificationDto(
                            mv.getMatchId(),
                            nickname,
                            verified,
                            verifiedAt,
                            mv.getStatus(),
                            meetVerified,
                            mv.getCompletedAt()
                    );
                })
                .toList();

        // QR은 Post 기준 공통 토큰이므로 현재 match row가 아니라 형제 row 중 token owner를 기준으로 응답
        // 등록자 화면에서 이미 QR이 발급된 상태를 안정적으로 판단하기 위함
        MeetVerification qrOwner = siblingMvList.stream()
                .filter(mv -> mv.getQrToken() != null)
                .findFirst()
                .orElse(meetVerification);

        return new MeetVerificationResponseDto (
                matchId,
                meetVerification.getStatus(),
                authorNickname,
                meetVerification.getAuthorPlaceVerifiedAt(),
                participants,
                qrOwner.getQrToken() != null, // Post 공통 QR 토큰이 있으면 발급
                qrOwner.getQrExpiresAt(),
                meetVerification.getCompletedAt(),
                meetVerification.getNoShowDecidedAt()
        );
    }

    // matchId → (applicantId, nickname) 을 담는 record
    public record ParticipantInfo(
            Long applicantId,
            String nickname
    ) {}
}
