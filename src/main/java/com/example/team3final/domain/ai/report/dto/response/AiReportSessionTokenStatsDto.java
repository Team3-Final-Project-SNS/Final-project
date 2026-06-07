package com.example.team3final.domain.ai.report.dto.response;

import java.time.LocalDateTime;

/**
 * 관리자 AI 대화 세션별 토큰 윈도우 추적용 응답 DTO입니다.
 */
public record AiReportSessionTokenStatsDto(
        String conversationId,
        long messageCount,
        long estimatedTokenTotal,
        int tokenWindowBudget,
        int sessionExpireMinutes,
        String windowPolicy,
        LocalDateTime lastMessageAt
) {
}
