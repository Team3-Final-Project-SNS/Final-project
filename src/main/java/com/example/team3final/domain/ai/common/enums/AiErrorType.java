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

    TIMEOUT("AI 호출 시간 초과"),
    RATE_LIMIT("AI API 요청 한도 초과"),
    SERVER_ERROR("서버 내부 오류"),
    INVALID_API_KEY("AI API 키 오류"),

    INVALID_RESPONSE("AI 응답 형식 오류"),
    SCHEMA_VALIDATION_FAILED("AI 응답 스키마 검증 실패"),

    TOOL_ERROR("AI Tool 호출 오류"),
    TOOL_TIMEOUT("AI Tool 호출 시간 초과"),
    TOOL_NOT_FOUND("AI Tool을 찾을 수 없음"),

    PROMPT_LOAD_ERROR("AI 프롬프트 로드 실패"),
    PROMPT_NOT_FOUND("AI 프롬프트를 찾을 수 없음"),

    CONTENT_FILTERED("AI 콘텐츠 필터링"),
    FALLBACK_FAILED("대체 응답 생성 실패"),

    UNKNOWN("알 수 없는 AI 오류");

    private final String description;
}
