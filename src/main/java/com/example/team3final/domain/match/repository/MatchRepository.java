package com.example.team3final.domain.match.repository;

import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

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
}
