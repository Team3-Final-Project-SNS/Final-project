package com.example.team3final.domain.post.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.PostException;
import com.example.team3final.domain.post.dto.response.GetPostResponseDto;
import com.example.team3final.domain.post.dto.response.GetPostsItemResponseDto;
import com.example.team3final.domain.post.dto.response.PostInfoDto;
import com.example.team3final.domain.post.entity.Post;
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

    /**
     * 게시글 단건 조회 — 도메인 간 호출용 (엔티티 반환)
     *
     * 사용처:
     * - 매칭 도메인: createMatch 시 게시글 검증 + 상태 변경 대상
     * - GPS 인증: 약속 장소 위경도 확인
     *
     * @param postId 조회할 게시글 ID
     * @return Post 엔티티
     * @throws PostException POST_001 — 게시글이 존재하지 않음
     */
    Post getPostById(Long postId);

    /**
     * 게시글 정보 조회 — 도메인 간 호출용 (DTO 반환)
     *
     * 사용처:
     * - GPS 인증: 약속 장소 좌표 + 만남 시간만 필요할 때
     *
     * ※ getPostById와의 차이: 엔티티가 아닌 DTO 반환 → 호출자가 도메인 메서드 호출 불가, 단순 값 조회용
     *
     * @throws PostException POST_001 — 게시글이 존재하지 않음
     */
    PostInfoDto getPostInfo(Long postId);

    /**
     * 게시글 상세 조회 — Controller에서 직접 호출 (명세서 4.3 getPost)
     *
     * 검증 흐름:
     * 1. 게시글 존재 확인 → 없으면 POST_001 (404)
     * 2. 같은 학교 게시글인지 확인 → 다르면 POST_002 (403)
     * 3. 작성자 == 조회자 비교 → isMine 결정
     * 4. User 도메인에서 작성자 정보(닉네임/학과/학번) 조회 후 DTO 조립
     *
     * @param postId        조회할 게시글 ID
     * @param currentUserId 현재 로그인 유저 ID (학교 격리 + isMine 판별용)
     * @return 상세 조회 응답 DTO
     * @throws PostException POST_001 — 게시글이 존재하지 않음
     * @throws PostException POST_002 — 다른 학교 게시글 접근
     */
    GetPostResponseDto getPost(Long postId, Long currentUserId);
}
