package com.example.team3final.domain.post.event;

/**
 * 매칭 AI 게시글 벡터 인덱스에서 게시글을 제거하라는 도메인 이벤트입니다.
 *
 * 모집 마감, 완료, 삭제, 만료처럼 사용자가 더 이상 신청할 수 없는 상태가 되면
 * 추천 검색 후보에서도 빠지도록 postId 기준으로 벡터 테이블 데이터를 삭제합니다.
 */
public record PostVectorDeleteEvent(Long postId) {
}
