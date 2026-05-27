package com.example.team3final.domain.admin.dispute.dto.response;

import com.example.team3final.domain.dispute.enums.DisputeStatus;
import com.example.team3final.domain.meet.enums.VerificationStatus;

import java.time.LocalDateTime;
import java.util.List;

public record GetAdminDisputeResponseDto (

        Long disputeId,
        Long matchId,
        String applicantNickname,               // submitter_id → User.nickname 변환값
        String reason,                          // 이의제기 사유
        DisputeStatus status,                   // 이의제기 상태
        VerificationStatus verificationStatus,  // MeetVerification에서 가져오는 값
        LocalDateTime authorPlaceVerifiedAt,       // 호스트(작성자) GPS 입장 시각
        LocalDateTime applicantPlaceVerifiedAt,    // 신청자 GPS 입장 시각
        LocalDateTime submittedAt,              // 이의제기 제출 시각
        List<ChatMessage> chatMessages          // 채팅 내역 (복수)
) {
    public record ChatMessage(

            Long senderId,
            String senderNickname,              // senderId → User.nickname 변환값
            String content,
            LocalDateTime createdAt
    ) {
        public static ChatMessage of(Long senderId, String senderNickname, String content, LocalDateTime createdAt) {
            return new ChatMessage(senderId, senderNickname, content, createdAt);
        }
    }

    public static GetAdminDisputeResponseDto of(
            Long disputeId,
            Long matchId,
            String applicantNickname,
            String reason,
            DisputeStatus status,
            VerificationStatus verificationStatus,
            LocalDateTime authorPlaceVerifiedAt,
            LocalDateTime applicantPlaceVerifiedAt,
            LocalDateTime submittedAt,
            List<ChatMessage> chatMessages
    ) {
        return new GetAdminDisputeResponseDto(
                disputeId,
                matchId,
                applicantNickname,
                reason,
                status,
                verificationStatus,
                authorPlaceVerifiedAt,
                applicantPlaceVerifiedAt,
                submittedAt,
                chatMessages
        );
    }
}
