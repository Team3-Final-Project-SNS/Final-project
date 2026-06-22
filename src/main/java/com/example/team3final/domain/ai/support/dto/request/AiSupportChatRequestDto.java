package com.example.team3final.domain.ai.support.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 고객센터 AI 채팅 요청 DTO입니다.
 *
 * 사용자의 자연어 문의와 멀티턴 대화를 묶기 위한 conversationId를 전달합니다.
 * conversationId가 없으면 서비스에서 새 대화 ID를 생성합니다.
 */
public record AiSupportChatRequestDto(
        String conversationId,

        @NotBlank(message = "메시지는 필수입니다.")
        String message
) {
}
