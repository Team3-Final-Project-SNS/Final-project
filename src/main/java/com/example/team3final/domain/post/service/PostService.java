package com.example.team3final.domain.post.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.PostException;
import com.example.team3final.domain.post.dto.request.CreatePostRequestDto;
import com.example.team3final.domain.post.dto.request.UpdatePostRequestDto;
import com.example.team3final.domain.post.dto.response.*;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import org.springframework.data.domain.Page;
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

    // 게시글 강제 삭제 사유를 받아서 환불된 포인트 반환
    int forceDeletePost(Post post, String reason);

    // 작성자 본인이 자신의 삭제된 게시글 사유 조회 (알림 진입로 / 마이페이지용)
    DeletedPostReasonResponseDto getDeletedPostReason(Long postId, Long userId);

    // 강제 삭제 게시글 복구
    int restorePost(Post post);

    // 삭제 포함 단건 조회
    Post getPostByIdIncludingDeleted(Long postId);

    // 비관적 락을 걸어서 게시글을 조회하는 메서드.
    // 관리자 삭제, 신고 접수처럼 동시성 제어가 필요한 흐름에서 사용.
    Post getPostByIdWithLock(Long postId);

    // 관리자 게시글 목록 조회
    Page<Post> getPostsForAdmin(List<Long> authorIds, PostStatus status, String keyword, Pageable pageable);

    // AI 매칭 도메인에서 pgvector 후보가 없거나 비활성화된 경우 MySQL 기준으로 모집 중 게시글을 조회합니다.
    List<Post> findAiMatchingCandidatePosts(
            List<Long> authorIds,
            Sort sort
    );

    // pgvector가 반환한 postId 후보를 MySQL posts 테이블에서 같은 학교 작성자, OPEN 상태, 미래 약속 시간 기준으로 최종 검증합니다.
    List<Post> findAiMatchingCandidatePostsByIds(
            List<Long> postIds,
            List<Long> authorIds
    );

    // ── 동시성 테스트용 추가 메서드 ──────────────────────────────────────

    /**
     * 게시글 단건 조회 + 비관적 락 (NOWAIT) — 동시성 테스트 전략 A 전용
     *
     * SELECT ... FOR UPDATE NOWAIT
     * → 다른 트랜잭션이 이 행을 이미 잠갔으면 기다리지 않고 즉시 LockTimeoutException 발생
     * → MatchConcurrencyService 전략 A에서 호출
     *
     * 기존 getPostById()와의 차이:
     *   getPostById()    → 일반 SELECT, 락 없음
     *   이 메서드         → SELECT FOR UPDATE NOWAIT, 즉시 실패
     *
     * @throws PostException POST_001 — 게시글이 존재하지 않음
     */
    Post getPostWithPessimisticLockNowait(Long postId);

    /**
     * 게시글 단건 조회 + 비관적 락 (대기 O) — 동시성 테스트 전략 B 전용
     *
     * SELECT ... FOR UPDATE (NOWAIT 없음)
     * → 다른 트랜잭션이 잠갔으면 innodb_lock_wait_timeout 설정값만큼 대기
     * → 대기 중 락이 해제되면 획득 성공, 시간 초과 시 예외
     * → MatchConcurrencyService 전략 B에서 호출
     *
     * 테스트 시 주의: innodb_lock_wait_timeout 기본 50초 → 테스트 DB는 3초로 낮출 것
     *   SET innodb_lock_wait_timeout = 3;
     *
     * @throws PostException POST_001 — 게시글이 존재하지 않음
     */
    Post getPostWithPessimisticLock(Long postId);

    /**
     * 게시글 상태 변경 — 동시성 테스트에서 MatchConcurrencyService가 호출하는 전용 메서드
     *
     * 내부 동작:
     *   post.changeStatus(status) 도메인 메서드 호출
     *   → JPA 변경감지(Dirty Checking)로 트랜잭션 종료 시 UPDATE 자동 발생
     *   → 명시적 save() 불필요
     *
     * @param postId  변경할 게시글 ID
     * @param status  변경할 상태값
     * @throws PostException POST_001 — 게시글이 존재하지 않음
     */
    void changePostStatus(Long postId, PostStatus status);
}
