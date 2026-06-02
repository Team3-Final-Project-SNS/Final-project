package com.example.team3final.domain.ai.support.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 고객센터 AI 대화 메시지의 작성 주체를 구분하는 enum입니다.
 *
 * 멀티턴 대화 저장 시 사용자 메시지와 AI 응답 메시지를
 * 같은 conversationId 아래에서 순서대로 구분하기 위해 사용합니다.
 */
@Getter
@RequiredArgsConstructor
public enum AiSupportMessageRole {

    USER("사용자 메시지"),
    ASSISTANT("AI 응답 메시지");

    private final String description;
}
