package com.example.team3final.domain.ai.report.tool;

import com.example.team3final.domain.dispute.enums.DisputeStatus;
import com.example.team3final.domain.dispute.enums.DisputeType;

import java.time.LocalDateTime;

/**
 * 관리자 AI가 닉네임이나 이의제기 사유 키워드로 이의제기 후보를 찾았을 때 사용하는 검색 결과입니다.
 */
public record AiDisputeSearchToolResult(
        Long disputeId,
        Long matchId,
        Long submitterId,
        String submitterNickname,
        DisputeType disputeType,
        String reason,
        DisputeStatus status,
        LocalDateTime submittedAt
) {
}
