package com.example.team3final.domain.post.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.PostException;
import com.example.team3final.domain.post.dto.request.CreatePostRequestDto;
import com.example.team3final.domain.post.dto.request.UpdatePostRequestDto;
import com.example.team3final.domain.post.dto.response.*;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;

public interface PostService {

    // ===================== Command (쓰기) =====================

    /**
     * 게시글 작성
     *
     * @param authorId 작성자 ID (Controller에서 인증 정보로 추출해 전달)
     * @param request  게시글 작성 요청 DTO
     * @return 생성된 게시글 정보
     */
    CreatePostResponseDto createPost(Long authorId, CreatePostRequestDto request);

    /**
     * 게시글 상태를 COMPLETED로 전환 — 도메인 간 호출용
     * 사용처: 매칭 도메인(completeMatch) — QR 인증 완료 시 Post도 함께 종료
     *
     * @throws PostException POST_001 — 게시글이 존재하지 않음
     */
    void completePost(Long postId);

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

    // ===================== Query (읽기) =====================

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
     * 게시글 단건 조회 — 도메인 간 호출용 (엔티티 반환)
     * 사용처: 매칭(createMatch 검증), GPS 인증(위경도 확인)
     *
     * @throws PostException POST_001 — 게시글이 존재하지 않음
     */
    Post getPostById(Long postId);

    /**
     * 게시글 정보 조회 — 도메인 간 호출용 (DTO 반환)
     * getPostById와 차이: 엔티티가 아닌 DTO 반환 → 단순 값 조회용
     *
     * @throws PostException POST_001 — 게시글이 존재하지 않음
     */
    PostInfoDto getPostInfo(Long postId);

    /**
     * 게시글 상세 조회 — Controller에서 직접 호출 (명세서 4.3)
     *
     * @throws PostException POST_001 — 게시글 없음 / POST_002 — 다른 학교 접근
     */
    GetPostResponseDto getPost(Long postId, Long currentUserId);

    /**
     * 게시글 매칭 정보 조회 — 도메인 간 호출용 (매칭 목록 조회 전용)
     */
    PostMatchInfoDto getPostMatchInfo(Long postId);

    /**
     * 게시글 정보 일괄 조회 — 도메인 간 호출용 (벌크)
     * 사용처: Meet 도메인 노쇼 일괄 판정(judgeGpsNoShow) — N건의 매칭 정보에 묶인
     *         Post(meetAt, placeLat/Lng 등)를 한 번의 IN 쿼리로 가져와 N+1 문제 방지
     *
     * Contract:
     *  - postIds 가 비어있거나 null이면 빈 Map 반환 (예외 던지지 않음)
     *  - 존재하지 않는 postId 가 섞여 있어도 예외를 던지지 않고, 결과 Map에서 빠진 채로 반환
     */
    Map<Long, PostInfoDto> getPostInfos(List<Long> postIds);

    /**
     * 게시글 매칭정보 일괄 조회 — 도메인 간 호출용 (벌크)
     * 사용처: 매칭 목록(getMatches) N+1 방지
     */
    Map<Long, PostMatchInfoDto> getPostMatchInfos(List<Long> postIds);

    // Admin 도메인에서 사용할 게시글 강제 삭제 후 환불된 포인트 반환
    int forceDeletePost(Post post);


    // ai 매칭 도메인에서 활용.
    List<Post> findAiMatchingCandidatePosts(
            List<Long> authorIds,
            Sort sort
    );
}
