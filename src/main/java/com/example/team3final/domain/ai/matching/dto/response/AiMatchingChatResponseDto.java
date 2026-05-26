package com.example.team3final.domain.ai.matching.dto.response;


import java.util.List;

public record AiMatchingChatResponseDto(

        String conversationId,

        String answer,

        List<RecommendedPostDto> recommendedPosts,

        boolean fallbackUsed
) {
}
