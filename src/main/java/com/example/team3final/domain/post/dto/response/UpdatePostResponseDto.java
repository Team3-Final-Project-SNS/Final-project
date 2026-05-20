package com.example.team3final.domain.post.dto.response;

import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;

import java.time.LocalDateTime;

public record UpdatePostResponseDto(
        Long postId,
        LocalDateTime meetAt,
        String placeName,
        int authorDeposit,
        PostStatus status,
        LocalDateTime updatedAt
) {
    public static UpdatePostResponseDto from(Post post) {
        return new UpdatePostResponseDto(
                post.getId(),
                post.getMeetAt(),
                post.getPlaceName(),
                post.getAuthorDeposit(),
                post.getStatus(),
                post.getUpdatedAt()
        );
    }
}
