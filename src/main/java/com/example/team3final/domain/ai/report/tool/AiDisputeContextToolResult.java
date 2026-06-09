package com.example.team3final.domain.ai.report.tool;

import com.example.team3final.domain.dispute.enums.DisputeStatus;
import com.example.team3final.domain.dispute.enums.DisputeType;
import com.example.team3final.domain.meet.enums.VerificationStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 AI가 이의제기 단건 분석에 사용할 Tool 결과입니다.
 *
 * 이의제기 사유, 만남 인증 상태, GPS 인증 시각, 관련 채팅 일부를 함께 제공해
 * LLM이 실제 운영 데이터 기반으로 보수적인 검토 의견을 만들 수 있게 합니다.
 */
public record AiDisputeContextToolResult(
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
        int chatMessageCount,
        List<ChatMessage> recentChatMessages
) {
    public record ChatMessage(
            Long senderId,
            String senderNickname,
            String content,
            LocalDateTime createdAt
    ) {
    }
}
