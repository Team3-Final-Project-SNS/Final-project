package com.example.team3final.domain.admin.post.dto.response;

import java.time.LocalDateTime;

public record AdminDeletePostResponseDto (

        Long postId,            // 삭제된 게시글 ID
        Long reportId,          // 삭제 근거가 된 신고 ID (감사 추적용)
        String reason,          // 삭제 사유
        int refundedPoint,      // 작성자에게 환불된 예치 포인트
        LocalDateTime deletedAt // 삭제 처리 시각
) {
    public static AdminDeletePostResponseDto of(
            Long postId,
            Long reportId,
            String reason,
            int refundedPoint,
            LocalDateTime deletedAt) {
        return new AdminDeletePostResponseDto(
                postId,
                reportId,
                reason,
                refundedPoint,
                deletedAt
        );
    }
}
