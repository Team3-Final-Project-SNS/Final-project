package com.example.team3final.domain.post.repository;

import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    // AI_report 관리자 콘솔 챗봇의 대시보드 요약용 읽기 전용 집계입니다.
    long countByStatus(PostStatus status);

    /**
     * soft delete된 게시글 포함 IN 조회 — 매칭 목록 조회 전용
     *
     * [왜 이 메서드가 필요한가]
     *   - 기본 findAllById()는 @SQLRestriction("deleted_at IS NULL") 때문에
     *     soft delete된 게시글을 결과에서 제외함
     *   - 매칭은 게시글이 삭제되어도 살아있음 (매칭 이력 보존)
     *   - 따라서 getMatches()에서 매칭에 엮인 postId로 게시글 정보를 조회할 때,
     *     삭제된 게시글도 포함해서 가져와야 postInfo가 null이 되지 않음
     *   - soft delete 필터(@SQLRestriction)를 우회하기 위해 @Query로 직접 작성
     *
     * [쿼리 설명]
     *   FROM Post p    : Post 엔티티 (deleted_at 컬럼 포함)
     *   WHERE p.id IN :postIds  : 주어진 ID 목록으로 일괄 조회
     *   (deleted_at 조건 없음 → soft delete 필터 우회)*
     */
    @Query("SELECT p FROM Post p WHERE p.id IN :postIds")
    List<Post> findAllByIdIncludingDeleted(@Param("postIds")List<Long> postIds);

    // 게시글을 PESSIMISTIC_WRITE 락으로 조회
    // 이 메서드가 트랜잭션 안에서 실행되면 해당 Post row에 write lock이 걸림.
    // 같은 게시글을 다른 트랜잭션에서 동시에 PESSIMISTIC_WRITE로 잡으려 하면 대기하게 됨.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Post p WHERE p.id = :postId")
    Optional<Post> findByIdWithLock(@Param("postId") Long postId);

    // soft delete된 게시글 포함 단건 조회 -> 삭제 사유 조회 전용
    @Query("""
           SELECT p FROM Post p WHERE p.id = :postId
           """)
    Optional<Post> findByIdIncludingDeleted(@Param("postId") Long postId);

    Page<Post> findByAuthorIdInAndStatus(
            List<Long> authorIds,
            PostStatus status,
            Pageable pageable
    );

    // 특정 작성자(authorId)의 게시글만 페이징 조회
    Page<Post> findByAuthorId(Long authorId, Pageable pageable);

    // status 조건 없이 학교 필터만 적용한 게시글 조회 (전체 상태 조회용)
    // getPosts()에서 status가 null일 때 사용
    Page<Post> findByAuthorIdIn(List<Long> authorIds, Pageable pageable);

    // 관리자 게시글 목록 조회 — 전체 대학 조회 시 사용 (universityId 필터 없을 때)
    @Query("""
        SELECT p FROM Post p
        WHERE (:status IS NULL OR p.status = :status)
          AND (:keyword IS NULL OR p.placeName LIKE %:keyword%)
        ORDER BY p.createdAt DESC
        """)
    Page<Post> findAllForAdmin(
            @Param("status") PostStatus status,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    // 관리자 게시글 목록 조회 — 특정 대학 필터 시 사용 (universityId 있을 때 해당 대학 작성자 ID 목록으로 조회)
    @Query("""
        SELECT p FROM Post p
        WHERE p.authorId IN :authorIds
          AND (:status IS NULL OR p.status = :status)
          AND (:keyword IS NULL OR p.placeName LIKE %:keyword%)
        ORDER BY p.createdAt DESC
        """)
    Page<Post> findAllForAdminByAuthorIds(
            @Param("authorIds") List<Long> authorIds,
            @Param("status") PostStatus status,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    /**
     * 만료 벌크 업데이트
     *
     * 조건: OPEN 상태이면서 meetAt이 현재 시각보다 과거인 게시글 전부
     * → 스케줄러가 1시간마다 이 쿼리 1번으로 전부 처리
     *
     * @Modifying: SELECT가 아닌 UPDATE/DELETE 쿼리임을 JPA에게 알림
     *   - clearAutomatically = true: 쿼리 실행 후 1차 캐시(EntityManager)를 비워줌
     *     → 이걸 안 하면 DB는 EXPIRED인데 메모리 안 엔티티는 OPEN으로 남는 불일치 발생
     *
     * @Query JPQL: 엔티티 필드명 기준으로 작성 (컬럼명 아님)
     *   - p.status = :open     → PostStatus.OPEN 값
     *   - p.meetAt < :now      → 만남 시각이 현재보다 과거
     *   - p.deletedAt IS NULL  → soft delete된 게시글 제외
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Post p
            SET p.status = :expired
            WHERE p.status = :open
              AND p.meetAt < :now
              AND p.deletedAt IS NULL
            """)
    int bulkExpireOpenPosts(
            @Param("open")    PostStatus open,    // PostStatus.OPEN
            @Param("expired") PostStatus expired, // PostStatus.EXPIRED
            @Param("now")     LocalDateTime now   // LocalDateTime.now()
    );



 // ai 매칭 도메인에서 활용. toolcalling에서 활용하기 위해서.
    Page<Post> findByAuthorIdInAndStatusAndMeetAtAfter(
            List<Long> authorIds,
            PostStatus status,
            LocalDateTime meetAt,
            Pageable pageable
    );
}
