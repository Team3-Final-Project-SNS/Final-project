package com.example.team3final.domain.admin.post.dto.response;

import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;

import java.time.LocalDateTime;

public record AdminGetPostsResponseDto(

        Long postId,
        String authorNickname,
        String placeName,
        String content,
        LocalDateTime meetAt,
        int authorDeposit,
        PostStatus status,
        LocalDateTime createdAt,
        boolean deleted,
        LocalDateTime deletedAt
) {
    public static AdminGetPostsResponseDto of(Post post, String authorNickname) {
        return new AdminGetPostsResponseDto(
                post.getId(),
                authorNickname,
                post.getPlaceName(),
                post.getContent(),
                post.getMeetAt(),
                post.getAuthorDeposit(),
                post.getStatus(),
                post.getCreatedAt(),
                post.isDeleted(),
                post.getDeletedAt()
        );
    }
}
