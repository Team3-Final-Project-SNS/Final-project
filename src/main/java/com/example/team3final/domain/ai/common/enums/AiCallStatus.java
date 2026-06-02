package com.example.team3final.domain.ai.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * AI 호출 처리 결과 상태입니다.
 *
 * AI 호출 성공, 실패, fallback 처리 여부를 기록하기 위해 사용합니다.
 */
@Getter
@RequiredArgsConstructor
public enum AiCallStatus {

    SUCCESS("AI 호출 성공"),
    FAILED("AI 호출 실패"),
    FALLBACK("대체 응답 사용");

    private final String description;
}
