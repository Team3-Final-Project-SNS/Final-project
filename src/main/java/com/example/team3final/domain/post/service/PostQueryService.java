package com.example.team3final.domain.post.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.post.dto.response.GetPostsItemResponseDto;
import com.example.team3final.domain.post.enums.PostStatus;
import org.springframework.data.domain.Pageable;

public interface PostQueryService {

    /**
     * 같은 학교 게시글 목록 조회
     *
     * @param currentUserId 현재 로그인 유저 ID (학교 판별용)
     * @param status        상태 필터 (null이면 OPEN 기본)
     * @param pageable      페이징 + 정렬 정보 (Controller에서 authorDeposit DESC로 생성)
     * @return 페이징 응답 DTO
     */
    PageResponseDto<GetPostsItemResponseDto> getPosts(
            Long currentUserId,
            PostStatus status,
            Pageable pageable
    );
}
