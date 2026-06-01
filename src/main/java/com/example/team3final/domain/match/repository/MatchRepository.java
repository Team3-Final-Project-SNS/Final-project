package com.example.team3final.domain.match.repository;

import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchRepository extends JpaRepository<Match, Long> {

    /**
     * 내 매칭 목록 전체 조회 (상태 필터 없음)
     *
     * [네이티브 쿼리로 전환한 이유]
     *   → SQL을 직접 쓰면 AND p.deleted_at IS NULL을 명시적으로 넣을 수 있음
     *   → soft delete 우회 문제를 완전히 해결
     *
     * [쿼리 설명]
     *    SELECT m.*           : matches 테이블의 모든 컬럼 (Match 엔티티로 매핑됨)
     *    FROM matches m       : 매칭 테이블
     *    JOIN posts p         : 게시글 테이블과 조인
     *     ON m.post_id = p.post_id
     *     AND p.deleted_at IS NULL  ← soft delete 필터 명시적 적용
     *   WHERE 내가 등록자 OR 신청자
     *   ORDER BY m.created_at DESC  : 최신 매칭이 위로 (페이지 정렬)
     *
     * nativeQuery = true: JPQL이 아닌 실제 SQL을 사용한다는 선언
     * countQuery: 페이징을 위해 전체 건수를 세는 별도 쿼리 (SELECT COUNT 최적화)
     *   → countQuery 없으면 Spring이 SELECT * 전체 결과로 count를 계산해서 느려짐
     */
    @Query(
            value = """
                SELECT m.*
                FROM matches m
                JOIN posts p
                  ON m.post_id = p.post_id
                 AND p.deleted_at IS NULL
                WHERE p.author_id = :userId
                   OR m.applicant_id = :userId
                ORDER BY m.created_at DESC
                """,
            countQuery = """
                SELECT COUNT(*)
                FROM matches m
                JOIN posts p
                  ON m.post_id = p.post_id
                 AND p.deleted_at IS NULL
                WHERE p.author_id = :userId
                   OR m.applicant_id = :userId
                """,
            nativeQuery = true
    )
    Page<Match> findAllByUserId(@Param("userId") Long userId, Pageable pageable);

    /**
     * 내 매칭 목록 상태 필터 조회
     *
     * findAllByUserId와 동일한 이유로 네이티브 쿼리로 전환
     * 추가 조건: AND m.status = :status
     *
     * :status는 MatchStatus enum의 name() 값 (문자열)이 바인딩됨
     */
    @Query(
            value = """
                SELECT m.*
                FROM matches m
                JOIN posts p
                  ON m.post_id = p.post_id
                 AND p.deleted_at IS NULL
                WHERE (p.author_id = :userId OR m.applicant_id = :userId)
                  AND m.status = :status
                ORDER BY m.created_at DESC
                """,
            countQuery = """
                SELECT COUNT(*)
                FROM matches m
                JOIN posts p
                  ON m.post_id = p.post_id
                 AND p.deleted_at IS NULL
                WHERE (p.author_id = :userId OR m.applicant_id = :userId)
                  AND m.status = :status
                """,
            nativeQuery = true
    )
    Page<Match> findAllByUserIdAndStatus(
            @Param("userId") Long userId,
            @Param("status") String status,
            Pageable pageable
    );

    // 특정 게시글에 특정 상태(주로 MATCHED)인 매칭이 현재 몇 건인지 카운트
    long countByPostIdAndStatus(Long postId, MatchStatus status);





    // 일단 ai db 활용을 위해서 임시로. 나중에 리팩토링할때 서비스 to 서비스로 변경 예정.
    // 특정 게시글에 사용자가 이미 신청했는지 확인
    boolean existsByPostIdAndApplicantId(Long postId, Long applicantId);
}
