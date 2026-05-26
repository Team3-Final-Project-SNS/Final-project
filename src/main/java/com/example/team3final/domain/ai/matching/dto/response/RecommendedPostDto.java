package com.example.team3final.domain.ai.matching.dto.response;


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