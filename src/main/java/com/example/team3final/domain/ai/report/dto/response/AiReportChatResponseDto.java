package com.example.team3final.domain.ai.report.dto.response;

import com.example.team3final.domain.ai.report.enums.AiReportChatAction;
import com.example.team3final.domain.ai.report.tool.AiReportDashboardToolResult;

/**
 * 관리자 신고 AI 챗봇 응답 DTO입니다.
 *
 * 챗봇이 선택한 기능과 자연어 답변을 함께 반환하고,
 * 실행 결과에 따라 단일 신고 분석 결과, 이의제기 분석 결과,
 * 고위험 유저 목록, 대시보드 운영 요약을 담습니다.
 */
public record AiReportChatResponseDto(
        String answer,
        AiReportChatAction action,
        AiReportAnalysisResponseDto reportAnalysis,
        AiReportDisputeAnalysisResponseDto disputeAnalysis,
        AiReportHighRiskUsersResponseDto highRiskUsers,
        AiReportDashboardToolResult dashboardSummary,
        boolean fallbackUsed
) {
}
