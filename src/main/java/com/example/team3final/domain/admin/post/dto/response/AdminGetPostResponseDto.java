package com.example.team3final.domain.admin.post.dto.response;

import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;

import java.time.LocalDateTime;

public record AdminGetPostResponseDto (

        Long postId,            // 게시글 ID
        PostStatus status,      // 게시글 상태
        int authorDeposit,      // 책임비 포인트
        String content,         // 한마디 (없으면 null)
        String placeName,       // 만남 장소명
        LocalDateTime meetAt,   // 만남 희망 시간
        String authorNickname,  // 작성자 닉네임
        LocalDateTime createdAt // 작성일
) {
    public static AdminGetPostResponseDto of(Post post, String authorNickname) {
        return new AdminGetPostResponseDto(
                post.getId(),
                post.getStatus(),
                post.getAuthorDeposit(),
                post.getContent(),      // 한마디 — null 가능
                post.getPlaceName(),
                post.getMeetAt(),
                authorNickname,
                post.getCreatedAt()
        );
    }
}
