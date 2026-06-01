package com.example.team3final.domain.admin.dispute.dto.response;

import com.example.team3final.domain.dispute.enums.DisputeStatus;
import com.example.team3final.domain.dispute.enums.DisputeType;
import com.example.team3final.domain.meet.enums.VerificationStatus;

import java.time.LocalDateTime;
import java.util.List;

public record GetAdminDisputeResponseDto(

        Long disputeId,                         // 이의제기 ID
        Long matchId,                           // 매칭 ID
        String applicantNickname,               // 이의제기 제출자 닉네임
        DisputeType disputeType,                // 이의제기 사유 타입 (GPS_ERROR 등)
        String reason,                          // 이의제기 사유 상세 텍스트
        DisputeStatus status,                   // 현재 이의제기 상태
        VerificationStatus verificationStatus,  // 해당 매칭의 만남인증 상태
        LocalDateTime authorPlaceVerifiedAt,    // 등록자 GPS 인증 시각 — 미인증이면 null
        LocalDateTime applicantPlaceVerifiedAt, // 신청자 GPS 인증 시각 — 미인증이면 null
        LocalDateTime submittedAt,              // 이의제기 제출 시각
        List<ChatMessage> chatMessages          // 해당 매칭 채팅 내역
) {
    public record ChatMessage(
            Long senderId,          // 발신자 유저 ID
            String senderNickname,  // 발신자 닉네임
            String content,         // 메시지 내용
            LocalDateTime createdAt // 메시지 전송 시각
    ) {
        public static ChatMessage of(Long senderId, String senderNickname,
                                     String content, LocalDateTime createdAt) {
            return new ChatMessage(senderId, senderNickname, content, createdAt);
        }
    }

    public static GetAdminDisputeResponseDto of(
            Long disputeId,
            Long matchId,
            String applicantNickname,
            DisputeType disputeType,
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
                disputeType,
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
