package com.example.team3final.domain.ai.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * AI 멀티턴 대화 메모리의 메시지 작성 주체를 구분하는 enum입니다.
 */
@Getter
@RequiredArgsConstructor
public enum AiChatMemoryRole {

    USER("사용자 메시지"),
    ASSISTANT("AI 응답 메시지");

    private final String description;
}
