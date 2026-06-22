package com.example.team3final.domain.admin.meet.dto.response;

import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.enums.VerificationStatus;

import java.time.LocalDateTime;

public record AdminNoShowCandidateResponseDto (

        Long matchId,
        VerificationStatus verificationStatus,
        String hostNickname,          // Post.authorId -> User.nickname
        String guestNickname,         // Match.applicantId -> User.nickname
        LocalDateTime meetAt,
        boolean hasDispute,           // 해당 matchId로 이의제기 제출 여부 (DisputeService에서 조회)
        LocalDateTime disputeDeadline // 노쇼 판정 시각 + 24시간
) {
    public static AdminNoShowCandidateResponseDto of (
            MeetVerification meetVerification,
            String hostNickname,
            String guestNickname,
            LocalDateTime meetAt,
            boolean hasDispute) {
        return new AdminNoShowCandidateResponseDto(
                meetVerification.getMatchId(),
                meetVerification.getStatus(),
                hostNickname,
                guestNickname,
                meetAt,
                hasDispute,
                meetVerification.getNoShowDecidedAt()
                        != null ? meetVerification.getNoShowDecidedAt().plusHours(24) : null
        );
    }
}
