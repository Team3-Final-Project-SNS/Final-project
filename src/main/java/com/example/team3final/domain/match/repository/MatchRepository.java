package com.example.team3final.domain.match.repository;

import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MatchRepository extends JpaRepository<Match, Long>, MatchRepositoryCustom {

    // 특정 게시글에 특정 상태(주로 MATCHED)인 매칭이 현재 몇 건인지 카운트
    long countByPostIdAndStatus(Long postId, MatchStatus status);

    // MATCHED 상태인 매칭만 중복으로 판단
    // CANCELLED 상태는 이미 취소된 것이므로 재신청 허용
    // SQL: SELECT EXISTS (
    //        SELECT 1 FROM matches
    //        WHERE post_id = ? AND applicant_id = ? AND status = 'MATCHED'
    //      )
    boolean existsByPostIdAndApplicantIdAndStatus(Long postId, Long applicantId, MatchStatus status);

    // Review 도메인에서 단체 만남의 전체 신청자 리뷰 평균을 계산할 때 사용합니다.
    List<Match> findAllByPostId(Long postId);

    // 만남 완료 후 채팅방 READ_ONLY 전환 시 신청자에게 후기 작성 유도 알림을 보낼 때 사용합니다.
    List<Match> findAllByPostIdAndStatus(Long postId, MatchStatus status);

    // matchId로 authorId 조회 - MeetReminderScheduler용
    @Query("SELECT p.authorId FROM Match m JOIN Post p ON m.postId = p.id WHERE m.id = :matchId")
    Long findAuthorIdByMatchId(@Param("matchId") Long matchId);

    // 특정 게시글의 전체 매칭 수 카운트
    long countByPostId(Long postId);
    // Spring Data JPA가 자동으로 "SELECT COUNT(*) FROM matches WHERE post_id = ?" 생성

    // Match 단건을 PESSIMISTIC_WRITE 락으로 조회
    // 동일 Match에 대해 QR 스캔이 동시에 들어왔을 때 중복 완료/중복 환급을 막기 위해 사용
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Match m WHERE m.id = :matchId")
    Optional<Match> findByIdWithLock(@Param("matchId") Long matchId);

    // 사용자가 당사자인 전체 매칭 ID 목록 조회 (등록자 또는 신청자)
    @Query("""
        SELECT m.id FROM Match m
        JOIN Post p ON m.postId = p.id
        WHERE p.authorId = :userId OR m.applicantId = :userId
        """)
    List<Long> findAllMatchIdsByUserId(@Param("userId") Long userId);


     //postId에 연결된 활성(MATCHED) 매칭 중 id가 가장 작은 1건 조회
     // - MATCHED 상태만 조회하므로 CANCELLED/COMPLETED/노쇼/이의제기 상태는 제외
     // - 그룹 미팅이어도 항상 동일한 매칭이 선택되도록 ORDER BY id ASC로 결정성 보장
    Optional<Match> findFirstByPostIdAndStatusOrderByIdAsc(Long postId, MatchStatus status);
}
