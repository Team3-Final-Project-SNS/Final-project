package com.example.team3final.domain.ai.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * AI 호출 실패 원인을 분류하는 enum입니다.
 *
 * 장애 분석, fallback 처리, 관리자 메트릭 조회에서 사용합니다.
 */
@Getter
@RequiredArgsConstructor
public enum AiErrorType {

    TIMEOUT,
    RATE_LIMIT,
    SERVER_ERROR,
    INVALID_API_KEY,

    INVALID_RESPONSE,
    SCHEMA_VALIDATION_FAILED,

    TOOL_ERROR,
    TOOL_TIMEOUT,
    TOOL_NOT_FOUND,

    PROMPT_LOAD_ERROR,
    PROMPT_NOT_FOUND,

    CONTENT_FILTERED,
    FALLBACK_FAILED,

    UNKNOWN
}
