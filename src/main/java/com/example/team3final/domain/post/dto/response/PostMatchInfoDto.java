package com.example.team3final.domain.post.dto.response;

import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;

import java.time.LocalDateTime;

public record PostMatchInfoDto(
        Long postId,
        Long authorId,
        PostStatus status,
        LocalDateTime meetAt,
        String placeName,
        int authorDeposit,
        int currentApplicants,
        int maxApplicants
) {
    public static PostMatchInfoDto from(Post post) {
        return new PostMatchInfoDto(
                post.getId(),
                post.getAuthorId(),
                post.getStatus(),
                post.getMeetAt(),
                post.getPlaceName(),
                post.getAuthorDeposit(),
                Math.max(post.getCurrentApplicants(), 1),
                Math.max(post.getMaxApplicants(), 2)
        );
    }
}
