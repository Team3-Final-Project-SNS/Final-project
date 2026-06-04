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
        return new GetPostsItemResponseDto(
                post.getId(),
                post.getAuthorId(),
                authorNickname,
                authorMajor,
                authorStudentNumber,
                authorMannerTemperature,
                post.getMeetAt(),
                post.getPlaceName(),
                post.getAuthorDeposit(),
                post.getCurrentApplicants(),
                post.getMaxApplicants(),
                post.getStatus(),
                post.getCreatedAt()
        );
    }
}
