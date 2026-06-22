package com.example.team3final.domain.post.dto.response;

import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GetPostsItemResponseDto(
        Long postId,
        Long authorId,
        String authorNickname,
        String authorMajor,
        String authorStudentNumber,
        BigDecimal authorMannerTemperature,
        LocalDateTime meetAt,
        String placeName,
        int authorDeposit,
        int currentApplicants,
        int maxApplicants,
        PostStatus status,
        LocalDateTime createAt
) {
    /**
     * Post 엔티티 + User 정보 → DTO 변환
     *
     * @param post                Post 엔티티
     * @param authorNickname      작성자 닉네임 (User 도메인에서 조회)
     * @param authorMajor         작성자 학과
     * @param authorStudentNumber 작성자 학번
     * @param authorMannerTemperature 작성자 매너온도
     */
    public static GetPostsItemResponseDto from(
            Post post,
            String authorNickname,
            String authorMajor,
            String authorStudentNumber,
            BigDecimal authorMannerTemperature
    ) {
        return from(post, authorNickname, authorMajor, authorStudentNumber, authorMannerTemperature, post.getMeetAt());
    }

    public static GetPostsItemResponseDto from(
            Post post,
            String authorNickname,
            String authorMajor,
            String authorStudentNumber,
            BigDecimal authorMannerTemperature,
            LocalDateTime meetAt
    ) {
        return new GetPostsItemResponseDto(
                post.getId(),
                post.getAuthorId(),
                authorNickname,
                authorMajor,
                authorStudentNumber,
                authorMannerTemperature,
                meetAt,
                post.getPlaceName(),
                post.getAuthorDeposit(),
                Math.max(post.getCurrentApplicants(), 1),
                Math.max(post.getMaxApplicants(), 2),
                post.getStatus(),
                post.getCreatedAt()
        );
    }
}
