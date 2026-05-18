package com.example.team3final.domain.post.service;

import com.example.team3final.domain.post.dto.request.CreatePostRequestDto;
import com.example.team3final.domain.post.dto.response.CreatePostResponseDto;

public interface PostCommandService {

    /**
     * 게시글 작성
     *
     * @param authorId 작성자 ID (Controller에서 인증 정보로 추출해 전달)
     * @param request  게시글 작성 요청 DTO
     * @return 생성된 게시글 정보
     */
    CreatePostResponseDto createPost(Long authorId, CreatePostRequestDto request);
}
