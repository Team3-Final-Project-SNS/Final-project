package com.example.team3final.domain.admin.post.dto.response;

import java.time.LocalDateTime;

public record AdminRestorePostResponseDto (

        Long postId,
        int redepositedPoint,   // 복구로 다시 차감(재예치)된 포인트
        LocalDateTime restoredAt
) {
    public static AdminRestorePostResponseDto of(Long postId, int redepositedPoint, LocalDateTime restoredAt) {
        return new AdminRestorePostResponseDto(postId, redepositedPoint, restoredAt);
    }
}
