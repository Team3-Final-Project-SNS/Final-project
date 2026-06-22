package com.example.team3final.domain.ai.rag.dto;

/**
 * RAG 문서 색인 결과 응답 DTO입니다.
 *
 * resources/rag-docs 아래 문서를 읽어 Document로 변환한 개수와
 * TokenTextSplitter를 거쳐 VectorStore에 저장한 chunk 개수를 반환합니다.
 */
public record AiRagIndexResponseDto(
        int sourceDocumentCount,
        int chunkCount,
        String message
) {
}
