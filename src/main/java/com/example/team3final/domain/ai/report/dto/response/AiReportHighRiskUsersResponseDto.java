package com.example.team3final.domain.ai.report.dto.response;

import java.util.List;

/**
 * 고위험 유저 목록 조회 API의 응답 DTO입니다.
 *
 * LLM이 생성한 전체 답변 문장과 유저별 분석 목록,
 * fallback 사용 여부를 함께 반환합니다.
 */
public record AiReportHighRiskUsersResponseDto(
        String answer,
        List<AiReportHighRiskUserDto> highRiskUsers,
        boolean fallbackUsed
) {
}
