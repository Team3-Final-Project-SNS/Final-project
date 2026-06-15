package com.example.team3final.domain.post.service;

import com.example.team3final.common.exception.PostException;
import com.example.team3final.domain.post.dto.request.CreatePostRequestDto;
import com.example.team3final.domain.post.dto.request.UpdatePostRequestDto;
import com.example.team3final.domain.post.dto.response.CreatePostResponseDto;
import com.example.team3final.domain.post.dto.response.DeletePostResponseDto;
import com.example.team3final.domain.post.dto.response.UpdatePostResponseDto;

// Post 도메인의 생성/수정/삭제 등 사용자 요청 기반 변경 작업을 담당하는 서비스
public interface PostCommandService {

    /**
     * 게시글 작성
     *
     * @param authorId 작성자 ID (Controller에서 인증 정보로 추출해 전달)
     * @param request  게시글 작성 요청 DTO
     * @return 생성된 게시글 정보
     */
    CreatePostResponseDto createPost(Long authorId, CreatePostRequestDto request);

    /**
     * 게시글 수정 — Controller에서 직접 호출 (명세서 4.4)
     *
     * @throws PostException POST_001/201/202/102 — 각 단계별 검증 실패
     */
    UpdatePostResponseDto updatePost(Long postId, Long userId, UpdatePostRequestDto request);

    /**
     * 게시글 삭제 — Controller에서 직접 호출 (명세서 4.5)
     *
     * @throws PostException POST_001/005/006 — 각 단계별 검증 실패
     */
    DeletePostResponseDto deletePost(Long postId, Long userId);
}
