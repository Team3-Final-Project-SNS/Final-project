package com.example.team3final.domain.admin.post.dto.response;

import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;

import java.time.LocalDateTime;

public record AdminGetPostResponseDto(

        Long postId,
        PostStatus status,
        int authorDeposit,
        String content,
        String placeName,
        LocalDateTime meetAt,
        String authorNickname,
        LocalDateTime createdAt,
        boolean deleted,
        LocalDateTime deletedAt
) {
    public static AdminGetPostResponseDto of(Post post, String authorNickname) {
        return new AdminGetPostResponseDto(
                post.getId(),
                post.getStatus(),
                post.getAuthorDeposit(),
                post.getContent(),
                post.getPlaceName(),
                post.getMeetAt(),
                authorNickname,
                post.getCreatedAt(),
                post.isDeleted(),
                post.getDeletedAt()
        );
    }
}
