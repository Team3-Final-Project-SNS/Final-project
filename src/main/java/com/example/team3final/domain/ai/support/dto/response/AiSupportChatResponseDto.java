package com.example.team3final.domain.ai.support.dto.response;

import com.example.team3final.domain.ai.support.enums.AiSupportCategory;

/**
 * 고객센터 AI 채팅 응답 DTO입니다.
 *
 * AI 답변, 분류된 문의 카테고리, 대화 요약, 추가 조치 필요 여부,
 * fallback 사용 여부를 클라이언트에 반환합니다.
 */
public record AiSupportChatResponseDto(
        String conversationId,
        String answer,
        AiSupportCategory category,
        String summary,
        boolean actionRequired,
        boolean fallbackUsed
) {
}
