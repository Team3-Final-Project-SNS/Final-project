package com.example.team3final.domain.ai.report.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AiAdminAnswerSource {

    TOOL("관리자 Tool 조회"),
    RAG("RAG 정책 문서"),
    TOOL_AND_RAG("관리자 Tool과 RAG 정책 문서"),
    GPT_GENERAL("GPT 일반 응답"),
    FALLBACK("Fallback 응답");

    private final String description;
}
