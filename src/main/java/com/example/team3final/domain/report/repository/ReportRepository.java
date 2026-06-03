package com.example.team3final.domain.report.repository;

import com.example.team3final.domain.report.entity.Report;
import com.example.team3final.domain.report.enums.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    // 내 신고 내역 조회 (최신순 페이징)
    Page<Report> findByReporterIdOrderByCreatedAtDesc(Long reporterId, Pageable pageable);

    // 특정 신고 조회 (본인 신고만)
    Optional<Report> findByIdAndReporterId(Long id, Long reporterId);

    // 이미 신고한 대상인지 확인 (중복 신고 방지)
    boolean existsByReporterIdAndTargetId(Long reporterId, Long targetId);

    // 관리자용 신고 목록 조회 - 상태 필터
    @Query("""
        SELECT r FROM Report r
        WHERE (:status IS NULL OR r.status = :status)
        ORDER BY r.createdAt DESC
        """)
    Page<Report> findAllByStatusFilter(
            @Param("status") ReportStatus status,
            Pageable pageable
    );

    // 피신고자 채택 횟수 조회 (제재 정책용)
    int countByTargetIdAndStatus(Long targetId, ReportStatus status);

    // 기각된 신고에 대해 3일 이내 재신고 확인
    boolean existsByReporterIdAndTargetIdAndStatusAndCreatedAtAfter(
            Long reporterId, Long targetId, ReportStatus status, LocalDateTime after);

    // 기각된 신고 단건 조회
    Optional<Report> findByReporterIdAndTargetIdAndStatus(Long reporterId, Long targetId, ReportStatus status);

    // 특정 신고자의 기각된 신고 횟수 조회
    int countByReporterIdAndStatus(Long reporterId, ReportStatus status);

    // 이번 달 신고자의 포상 지급 횟수 조회
    // → 횟수 * 50P로 이번 달 지급 총액 계산
    @Query("""
       SELECT COUNT(r)
       FROM Report r
       WHERE r.reporterId = :reporterId
         AND r.status = 'ACCEPTED'
         AND r.isRewarded = true
         AND r.processedAt >= :startOfMonth
       """)
    int countRewardedThisMonth(
            @Param("reporterId") Long reporterId,
            @Param("startOfMonth") LocalDateTime startOfMonth
    );
}
