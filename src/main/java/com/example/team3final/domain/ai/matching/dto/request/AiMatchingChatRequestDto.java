package com.example.team3final.domain.ai.matching.dto.request;

import jakarta.validation.constraints.NotBlank;

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
