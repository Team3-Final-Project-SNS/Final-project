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
        boolean hasDispute,           // TODO: Dispute 도메인 구현되면 실제 조회로 변경
        LocalDateTime disputeDeadline // meetAt 24시간
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
                meetAt.plusHours(24) // 이의제기 마감 -> 노쇼 판정 후 24시간
        );
    }
}
