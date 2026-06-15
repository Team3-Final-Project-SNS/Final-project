package com.example.team3final.domain.post.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.PostException;
import com.example.team3final.domain.post.dto.response.DeletedPostReasonResponseDto;
import com.example.team3final.domain.post.dto.response.GetPostResponseDto;
import com.example.team3final.domain.post.dto.response.GetPostsItemResponseDto;
import com.example.team3final.domain.post.enums.PostStatus;
import org.springframework.data.domain.Pageable;

// Post 도메인의 조회 기능을 담당하는 서비스
public interface PostQueryService {

    /**
     * 같은 학교 게시글 목록 조회
     *
     * @param status null이면 OPEN 기본
     * @param pageable 페이징 + 정렬 (Controller에서 authorDeposit DESC로 생성)
     */
    PageResponseDto<GetPostsItemResponseDto> getPosts(
            Long currentUserId,
            PostStatus status,
            Pageable pageable
    );

    /**
     * 작성자 기준 게시글 목록 조회 — 본인이 작성한 게시글만 페이징 반환
     *
     * @param authorId 작성자 ID
     * @param pageable 페이징 + 정렬
     */
    PageResponseDto<GetPostsItemResponseDto> getPostsByAuthor(
            Long authorId,
            Pageable pageable
    );

    /**
     * 게시글 상세 조회 — Controller에서 직접 호출 (명세서 4.3)
     *
     * @throws PostException POST_001 — 게시글 없음 / POST_002 — 다른 학교 접근
     */
    GetPostResponseDto getPost(Long postId, Long currentUserId);

    // 작성자 본인이 자신의 삭제된 게시글 사유 조회 (알림 진입로 / 마이페이지용)
    DeletedPostReasonResponseDto getDeletedPostReason(Long postId, Long userId);
}
