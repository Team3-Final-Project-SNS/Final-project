package com.example.team3final.domain.admin.post.service;

import com.example.team3final.domain.admin.post.dto.request.AdminDeletePostRequestDto;
import com.example.team3final.domain.admin.post.dto.response.AdminDeletePostResponseDto;

public interface AdminPostService {

    // 관리자 게시글 강제 삭제
    AdminDeletePostResponseDto deletePost(Long adminId, Long postId, AdminDeletePostRequestDto requestDto);
}
