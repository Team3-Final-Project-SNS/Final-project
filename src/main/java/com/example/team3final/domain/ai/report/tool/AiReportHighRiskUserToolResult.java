package com.example.team3final.domain.ai.report.tool;

import java.util.List;

/**
 * 고위험 유저 후보 조회 Tool의 내부 결과입니다.
 *
 * 유저별 신고 누적 수와 관련 신고 ID를 모아 LLM이 관리자에게
 * 우선 검토할 유저와 판단 이유를 정리할 수 있게 합니다.
 */
public record AiReportHighRiskUserToolResult(
        Long userId,
        String nickname,
        int totalReportCount,
        int pendingReportCount,
        int acceptedReportCount,
        int rejectedReportCount,
        List<Long> relatedReportIds,
        String reasonSummary
) {
}
