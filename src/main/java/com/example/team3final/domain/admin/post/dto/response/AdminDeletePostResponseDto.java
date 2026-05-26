package com.example.team3final.domain.admin.post.dto.response;

import java.time.LocalDateTime;

public record AdminDeletePostResponseDto (

        Long postId,
        String reason,
        int refundedPoint,
        LocalDateTime deletedAt
) {
    public static AdminDeletePostResponseDto of(Long postId, String reason, int refundedPoint) {
        return new AdminDeletePostResponseDto(
                postId,
                reason,
                refundedPoint,
                LocalDateTime.now()
        );
    }
}
