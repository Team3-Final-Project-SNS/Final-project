package com.example.team3final.domain.ai.common.enums;

/**
 * AI 호출 실패 원인을 분류하는 enum입니다.
 *
 * 장애 분석, fallback 처리, 관리자 메트릭 조회에서 사용합니다.
 */
public enum AiErrorType {

    TIMEOUT,
    RATE_LIMIT,
    SERVER_ERROR,
    INVALID_RESPONSE,
    INVALID_API_KEY,
    TOOL_ERROR,
    PROMPT_LOAD_ERROR,
    UNKNOWN
}