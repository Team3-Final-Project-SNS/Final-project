package com.example.team3final.domain.post.repository;

import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    Page<Post> findByAuthorIdInAndStatus(
            List<Long> authorIds,
            PostStatus status,
            Pageable pageable
    );

    // 특정 작성자(authorId)의 게시글만 페이징 조회
    Page<Post> findByAuthorId(Long authorId, Pageable pageable);

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
