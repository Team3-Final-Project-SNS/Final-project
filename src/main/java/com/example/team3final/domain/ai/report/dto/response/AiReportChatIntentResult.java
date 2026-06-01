package com.example.team3final.domain.ai.report.dto.response;

import com.example.team3final.domain.ai.report.enums.AiReportChatAction;

/**
 * 신고 AI 챗봇이 사용자 메시지를 해석한 의도 분류 결과입니다.
 *
 * LLM은 관리자 메시지를 보고 실행할 기능, 신고 ID, 조회 개수,
 * 추가 질문 문구를 이 구조에 맞춰 반환합니다.
 */
public record AiReportChatIntentResult(
        AiReportChatAction action,
        Long reportId,
        Integer limit,
        String clarificationMessage
) {
}
