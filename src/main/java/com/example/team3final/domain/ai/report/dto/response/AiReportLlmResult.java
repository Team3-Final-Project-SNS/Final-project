package com.example.team3final.domain.ai.report.dto.response;

import com.example.team3final.domain.ai.report.enums.AiReportDecisionSuggestion;
import com.example.team3final.domain.ai.report.enums.AiReportRiskLevel;

/**
 * LLM이 단일 신고 분석 후 반환해야 하는 구조화 응답 스키마입니다.
 *
 * Spring AI가 모델 응답을 이 record로 파싱하고,
 * 서비스는 이 값을 AiReportSummary 엔티티로 저장합니다.
 */
public record AiReportLlmResult(
        AiReportRiskLevel riskLevel,
        AiReportDecisionSuggestion decisionSuggestion,
        String summary,
        String evidence,
        String recommendationReason,
        String actionGuide,
        Integer confidenceScore,
        Boolean needsAdminReview
) {
}
