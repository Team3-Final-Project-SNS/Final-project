package com.example.team3final.domain.ai.report.dto.response;

import com.example.team3final.domain.ai.report.enums.AiReportRiskLevel;

import java.util.List;

/**
 * 관리자에게 표시할 고위험 유저 한 명의 AI 분석 결과 DTO입니다.
 *
 * 신고 누적 현황, 위험도, 판단 요약, 권장 조치를 함께 담아
 * 관리자 화면에서 우선 검토 대상을 빠르게 파악할 수 있게 합니다.
 */
public record AiReportHighRiskUserDto(
        Long userId,
        String nickname,
        AiReportRiskLevel riskLevel,
        int totalReportCount,
        int pendingReportCount,
        int acceptedReportCount,
        String reasonSummary,
        String recommendedAction,
        List<Long> relatedReportIds
) {
}
