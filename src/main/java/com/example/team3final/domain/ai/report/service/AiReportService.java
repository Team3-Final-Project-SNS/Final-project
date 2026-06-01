package com.example.team3final.domain.ai.report.service;

import com.example.team3final.domain.ai.report.dto.request.AiReportChatRequestDto;
import com.example.team3final.domain.ai.report.dto.response.AiReportAnalysisResponseDto;
import com.example.team3final.domain.ai.report.dto.response.AiReportChatResponseDto;
import com.example.team3final.domain.ai.report.dto.response.AiReportHighRiskUsersResponseDto;

/**
 * 신고 AI 기능의 서비스 계약입니다.
 *
 * 컨트롤러가 사용할 신고 분석과 고위험 유저 조회 기능을 정의하여
 * 구현체의 AI 호출, 저장, fallback 처리 세부사항을 감춥니다.
 */
public interface AiReportService {

    AiReportChatResponseDto chat(Long adminId, AiReportChatRequestDto request);

    AiReportAnalysisResponseDto analyzeReport(Long adminId, Long reportId);

    AiReportHighRiskUsersResponseDto getHighRiskUsers(Long adminId, int limit);
}
