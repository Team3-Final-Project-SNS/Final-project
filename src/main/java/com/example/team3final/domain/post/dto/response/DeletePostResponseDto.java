package com.example.team3final.domain.post.dto.response;

public record DeletePostResponseDto(
        Long postId,
        int refundedPoint  // 전액 환불 = 삭제 시점의 authorDeposit
) {
    /**
     * 삭제된 게시글 ID + 환불 포인트로 응답 생성
     *
     * @param postId        삭제된 게시글 ID
     * @param refundedPoint 환불된 책임비 포인트 (삭제 전 authorDeposit 값)
     */
    public static DeletePostResponseDto of(Long postId, int refundedPoint) {
        return new DeletePostResponseDto(postId, refundedPoint);
    }
}
