package com.example.team3final.domain.post.dto.response;

import com.example.team3final.domain.post.entity.Post;

import java.time.LocalDateTime;

public record PostMatchInfoDto(
        Long postId,
        Long authorId,
        LocalDateTime meetAt,
        String placeName,
        int authorDeposit
) {
    public static PostMatchInfoDto from(Post post) {
        return new PostMatchInfoDto(
                post.getId(),
                post.getAuthorId(),
                post.getMeetAt(),
                post.getPlaceName(),
                post.getAuthorDeposit()
        );
    }
}
