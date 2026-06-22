package com.example.team3final.domain.ai.report.dto.response;

import com.example.team3final.domain.ai.report.entity.AiAdminResult;
import com.example.team3final.domain.ai.report.enums.AiReportDecisionSuggestion;
import com.example.team3final.domain.ai.report.enums.AiReportRiskLevel;
import com.example.team3final.domain.report.enums.ReportReason;

import java.time.LocalDateTime;

/**
 * 관리자에게 반환되는 단일 신고 AI 분석 응답 DTO입니다.
 *
 * 저장된 AI 분석 요약, 위험도, 처리 제안, 판단 근거, 관리자 액션 가이드,
 * fallback 여부를 화면에서 바로 사용할 수 있는 형태로 제공합니다.
 */
public record AiReportAnalysisResponseDto(
        Long summaryId,
        Long reportId,
        ReportReason reportReason,
        AiReportDecisionSuggestion decisionSuggestion,
        AiReportRiskLevel riskLevel,
        String summary,
        String evidence,
        String recommendationReason,
        String actionGuide,
        Integer confidenceScore,
        boolean needsAdminReview,
        boolean fallbackUsed,
        LocalDateTime createdAt
) {

    public static AiReportAnalysisResponseDto of(AiAdminResult result, String actionGuide) {
        return new AiReportAnalysisResponseDto(
                result.getId(),
                result.getTargetId(),
                result.getReportReason(),
                result.getDecisionSuggestion(),
                result.getRiskLevel(),
                result.getSummary(),
                result.getEvidence(),
                result.getRecommendation(),
                actionGuide,
                result.getConfidenceScore(),
                result.isNeedsAdminReview(),
                result.isFallbackUsed(),
                result.getCreatedAt()
        );
    }
}
