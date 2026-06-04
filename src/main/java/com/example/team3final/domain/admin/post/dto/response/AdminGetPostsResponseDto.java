package com.example.team3final.domain.admin.post.dto.response;

import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;

import java.time.LocalDateTime;

// 관리자 게시글 목록 조회 응답 DTO
public record AdminGetPostsResponseDto(

        Long postId,            // 게시글 ID — 프론트가 강제 삭제 API 호출 시 사용
        String authorNickname,  // 작성자 닉네임 — UserService S2S bulk 조회로 채움
        String placeName,       // 만남 장소명
        String content,         // 한마디 — 목록에서 빠르게 확인하기 위해 노출
        LocalDateTime meetAt,   // 만남 희망 시간
        int authorDeposit,      // 책임비 포인트
        PostStatus status,      // 게시글 상태
        LocalDateTime createdAt // 생성일
) {
    // Post 엔티티 + 닉네임을 받아서 DTO 조립
    // nickname은 Post에 없어서 따로 파라미터로 받음
    public static AdminGetPostsResponseDto of(Post post, String authorNickname) {
        return new AdminGetPostsResponseDto(
                post.getId(),
                authorNickname,
                post.getPlaceName(),
                post.getContent(),
                post.getMeetAt(),
                post.getAuthorDeposit(),
                post.getStatus(),
                post.getCreatedAt()
        );
    }
}
