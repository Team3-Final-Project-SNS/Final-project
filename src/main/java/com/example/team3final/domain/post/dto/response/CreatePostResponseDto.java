package com.example.team3final.domain.post.dto.response;

import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreatePostResponseDto(
        Long postId,
        Long authorId,
        String authorNickname,
        LocalDateTime meetAt,
        String placeName,
        BigDecimal placeLat,
        BigDecimal placeLng,
        String content,
        int authorDeposit,
        PostStatus status,
        LocalDateTime createdAt
) {
    public static CreatePostResponseDto from(Post post, String authorNickname) {
        return new CreatePostResponseDto(
                post.getId(),
                post.getAuthorId(),
                authorNickname,
                post.getMeetAt(),
                post.getPlaceName(),
                post.getPlaceLat(),
                post.getPlaceLng(),
                post.getContent(),
                post.getAuthorDeposit(),
                post.getStatus(),
                post.getCreatedAt()
        );
    }
}
