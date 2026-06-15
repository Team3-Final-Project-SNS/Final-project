package com.example.team3final.domain.post.event;

import com.example.team3final.domain.post.enums.PostStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 매칭 AI 게시글 벡터 인덱스에 게시글을 저장하거나 갱신하라는 도메인 이벤트입니다.
 *
 * 게시글 생성/수정/복구처럼 추천 후보로 다시 노출되어야 하는 시점에 발행합니다.
 * 이벤트에는 pgvector 테이블에 필요한 검색 텍스트와 필터링 메타데이터만 담고,
 * 실제 게시글의 최종 정합성은 MySQL posts 테이블에서 다시 검증합니다.
 */
public record PostVectorUpsertEvent(
        Long postId,
        Long authorId,
        Long universityId,
        PostStatus status,
        LocalDateTime meetAt,
        String title,
        String description,
        int authorDeposit,
        int maxApplicants,
        int currentApplicants,
        BigDecimal placeLat,
        BigDecimal placeLng
) {
}
