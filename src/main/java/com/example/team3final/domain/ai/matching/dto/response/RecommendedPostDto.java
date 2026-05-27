package com.example.team3final.domain.ai.matching.dto.response;

/**
 * 매칭 AI 추천 후보 게시글 DTO입니다.
 *
 * AI 응답 화면에서 참고 후보 카드로 표시할 게시글 정보를 담습니다.
 * 현재는 LLM이 최종 선택한 게시글만이 아니라,
 * 백엔드 Tool 조회를 통해 확보한 후보 게시글 정보를 내려주는 용도로 사용합니다.
 */
public record RecommendedPostDto(

        Long postId,

        String placeName,

        String meetAt,

        int deposit,

        String reason,

        boolean applicationAvailable,

        boolean pointAffordable
) {
}