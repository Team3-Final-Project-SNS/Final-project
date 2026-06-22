package com.example.team3final.domain.ai.matching.dto.response;


import java.util.List;


/**
 * 매칭 AI 채팅 응답 DTO입니다.
 *
 * AI가 생성한 자연어 추천 답변과 추천 후보 게시글 목록,
 * fallback 응답 사용 여부를 클라이언트에 전달합니다.
 */

public record AiMatchingChatResponseDto(

        String conversationId,

        String answer,

        List<RecommendedPostDto> recommendedPosts,

        boolean fallbackUsed
) {
}
