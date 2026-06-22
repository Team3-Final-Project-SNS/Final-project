package com.example.team3final.domain.ai.matching.dto.request;

import jakarta.validation.constraints.NotBlank;


/**
 * 매칭 AI 채팅 요청 DTO입니다.
 *
 * 사용자가 입력한 자연어 식사 조건과
 * 멀티턴 대화 확장을 위한 conversationId를 전달합니다.
 *
 * 현재는 일반 단건 채팅 응답에 사용하며,
 * conversationId는 추후 대화 히스토리 저장 및 멀티턴 맥락 유지에 활용할 수 있습니다.
 */
public record AiMatchingChatRequestDto(

        /**
         * 멀티턴 대화 세션 ID입니다.
         * 첫 요청이면 null일 수 있다.
         */
        String conversationId,

        /**
         * 사용자의 자연어 요청.
         * 예: 오늘 저녁 조용하게 밥 먹을 사람 있어?
         */
        @NotBlank(message = "메시지는 필수입니다.")
        String message
) {
}
