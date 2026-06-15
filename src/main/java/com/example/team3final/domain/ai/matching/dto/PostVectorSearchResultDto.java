package com.example.team3final.domain.ai.matching.dto;

/**
 * PostgreSQL pgvector 게시글 검색 결과입니다.
 *
 * MySQL의 실제 게시글 데이터와 분리된 매칭 AI 전용 벡터 테이블에서
 * 의미 기반 후보 postId와 검색용 텍스트를 반환합니다.
 */
public record PostVectorSearchResultDto(
        Long postId,
        String title,
        String description,
        double similarity
) {
}
