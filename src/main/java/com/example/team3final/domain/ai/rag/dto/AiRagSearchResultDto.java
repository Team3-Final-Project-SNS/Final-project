package com.example.team3final.domain.ai.rag.dto;

/**
 * RAG Retriever가 반환하는 단일 검색 결과입니다.
 *
 * content는 LLM 프롬프트에 주입할 정책 문서 조각이고,
 * source는 사용자에게 출처로 보여줄 문서 메타데이터입니다.
 */
public record AiRagSearchResultDto(
        String content,
        AiRagSourceDto source,
        Double score
) {
}
