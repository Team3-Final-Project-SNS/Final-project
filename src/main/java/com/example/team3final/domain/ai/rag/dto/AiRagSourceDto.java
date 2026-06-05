package com.example.team3final.domain.ai.rag.dto;

/**
 * RAG 검색 결과의 출처 정보입니다.
 *
 * LLM 답변 마지막에 어떤 정책 문서를 근거로 사용했는지 표시하기 위해
 * VectorStore Document metadata에서 source, title, feature 값을 추출합니다.
 */
public record AiRagSourceDto(
        String title,
        String source,
        String feature
) {
}
