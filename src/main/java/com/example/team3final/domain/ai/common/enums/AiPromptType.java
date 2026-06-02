package com.example.team3final.domain.ai.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * AI 프롬프트의 용도를 구분하는 enum입니다.
 * 실제 프롬프트 파일명과 활성 버전은 AiPromptTemplate 엔티티에서 관리하고,
 * 이 enum은 MATCHING_CHAT, SUPPORT_CHAT처럼 프롬프트 종류를 식별하는 데 사용합니다.
 */

@Getter
@RequiredArgsConstructor
public enum AiPromptType {

    MATCHING_CHAT("매칭 AI 채팅 프롬프트"),
    SUPPORT_CHAT("고객센터 AI 채팅 프롬프트"),
    REPORT_SUMMARY("신고 AI 분석 프롬프트");


    private final String description;
}
