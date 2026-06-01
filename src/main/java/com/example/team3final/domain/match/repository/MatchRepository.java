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
     * 내가 등록자인 경우 → Post 서브쿼리로 authorId 확인
     * 내가 신청자인 경우 → match.applicantId 직접 확인
     * 두 조건을 OR로 합침
     *
     * JPQL 설명:
     * - Match m          : Match 엔티티를 m으로 별칭
     * - Post p           : Post 엔티티를 p로 별칭 (m.postId == p.id로 연결)
     * - p.authorId = :userId  : 내가 등록자인 매칭
     * - m.applicantId = :userId : 내가 신청자인 매칭
     */
    @Query("SELECT m FROM Match m JOIN Post p ON m.postId = p.id " +
            "WHERE p.authorId = :userId OR m.applicantId = :userId")
    Page<Match> findAllByUserId(@Param("userId") Long userId, Pageable pageable);

    /**
     * 내 매칭 목록 상태 필터 조회
     *
     * findAllByUserId와 동일하되 status 조건 추가
     */
    @Query("SELECT m FROM Match m JOIN Post p ON m.postId = p.id " +
            "WHERE (p.authorId = :userId OR m.applicantId = :userId) " +
            "AND m.status = :status")
    Page<Match> findAllByUserIdAndStatus(
            @Param("userId") Long userId,
            @Param("status") MatchStatus status,
            Pageable pageable
    );

    // 특정 게시글에 특정 상태(주로 MATCHED)인 매칭이 현재 몇 건인지 카운트
    long countByPostIdAndStatus(Long postId, MatchStatus status);





    // 일단 ai db 활용을 위해서 임시로. 나중에 리팩토링할때 서비스 to 서비스로 변경 예정.
    // 특정 게시글에 사용자가 이미 신청했는지 확인
    boolean existsByPostIdAndApplicantId(Long postId, Long applicantId);

    // matchId로 authorId 조회 - MeetReminderScheduler용
    @Query("SELECT p.authorId FROM Match m JOIN Post p ON m.postId = p.id WHERE m.id = :matchId")
    Long findAuthorIdByMatchId(@Param("matchId") Long matchId);
}
