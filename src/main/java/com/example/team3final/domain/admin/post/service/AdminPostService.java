package com.example.team3final.domain.admin.post.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.admin.post.dto.request.AdminDeletePostRequestDto;
import com.example.team3final.domain.admin.post.dto.response.AdminDeletePostResponseDto;
import com.example.team3final.domain.admin.post.dto.response.AdminGetPostsResponseDto;
import com.example.team3final.domain.post.enums.PostStatus;
import org.springframework.data.domain.Pageable;

public interface AdminPostService {

    // 관리자 게시글 강제 삭제
    AdminDeletePostResponseDto deletePost(Long adminId, Long postId, AdminDeletePostRequestDto requestDto);

    // 관리자 게시글 목록 조회
    PageResponseDto<AdminGetPostsResponseDto> getPosts(
            Long adminId,
            Long universityId, // 추가 — null이면 전체 대학
            PostStatus status,
            String keyword,
            Pageable pageable
    );
}
