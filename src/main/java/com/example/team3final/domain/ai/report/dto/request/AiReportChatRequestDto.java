package com.example.team3final.domain.ai.report.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 관리자 신고 AI 챗봇 요청 DTO입니다.
 *
 * 관리자가 버튼 없이 입력한 자연어 메시지를 받아
 * 신고 분석 또는 고위험 유저 조회 중 어떤 기능을 실행할지 판단하는 데 사용합니다.
 */
public record AiReportChatRequestDto(
        @NotBlank(message = "메시지는 필수입니다.")
        String message
) {
}