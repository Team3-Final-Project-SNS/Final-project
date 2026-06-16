package com.example.team3final.domain.dispute.repository;

import com.example.team3final.domain.dispute.entity.Dispute;
import com.example.team3final.domain.dispute.enums.DisputeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DisputeRepository extends JpaRepository<Dispute, Long> {

    // 같은 매칭에 대해 같은 유저가 이미 이의제기를 냈는지 검사.
    boolean existsByMatchIdAndSubmitterId(Long matchId, Long submitterId);

    // 특정 매칭에 대해 특정 유저가 제출한 이의제기 1건 조회.
    Optional<Dispute> findByMatchIdAndSubmitterId(Long matchId, Long submitterId);

    // status 필터링 + 페이징 조회 — 어드민 목록 조회용
    Page<Dispute> findAllByStatus(DisputeStatus status, Pageable pageable);

    long countByStatus(DisputeStatus status);

    // matchId만으로 이의제기 존재 여부 확인 (노쇼 후보군 조회용 벌크)
    @Query("""
           SELECT d.matchId
           FROM Dispute d
           WHERE d.matchId IN :matchIds
           """)
    List<Long> findMatchIdsByMatchIdIn(@Param("matchIds") List<Long> matchIds);

    /**
     * HOLD 상태인 이의제기 조회 (재이의제기 신청 시 원본 검증용).
     */
    @Query("""
       SELECT d
       FROM Dispute d
       WHERE d.matchId = :matchId
         AND d.submitterId = :submitterId
         AND d.status = 'HOLD'
       """)
    Optional<Dispute> findHoldDisputeByMatchIdAndSubmitterId(
            @Param("matchId") Long matchId,
            @Param("submitterId") Long submitterId);

    // 재이의제기 중복 제출 방지용
    boolean existsByMatchIdAndSubmitterIdAndParentDisputeId(Long matchId, Long submitterId, Long parentDisputeId);

    // 특정 matchId 목록 중 지정 상태인 이의제기의 matchId만 반환
    @Query("SELECT d.matchId FROM Dispute d WHERE d.matchId IN :matchIds AND d.status IN :statuses")
    List<Long> findMatchIdsByMatchIdInAndStatusIn(
            @Param("matchIds") List<Long> matchIds,
            @Param("statuses") List<DisputeStatus> statuses
    );

    // 내가 제출한 이의제기 전체 목록 조회 — 최신 제출순(createdAt DESC) 정렬
    // submitterId = 로그인 유저 ID
    // JPA 메서드 네이밍 규칙: findAllBy + 필드명 + OrderBy + 필드명 + Desc
    // → SQL 없이 메서드 이름만으로 쿼리가 자동 생성됨
    List<Dispute> findAllBySubmitterIdOrderByCreatedAtDesc(Long submitterId);
}