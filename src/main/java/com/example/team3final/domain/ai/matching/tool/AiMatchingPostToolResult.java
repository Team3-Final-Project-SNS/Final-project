package com.example.team3final.domain.ai.matching.tool;

public record AiMatchingPostToolResult(
        Long postId,
        String placeName,
        String meetAt,
        int deposit,
        String content,
        boolean applicationAvailable,
        boolean pointAffordable,
        String unavailableReason
) {
}