package com.example.team3final.domain.report.repository;

import com.example.team3final.domain.report.entity.Report;
import com.example.team3final.domain.report.enums.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    // AI_report 관리자 콘솔 챗봇의 대시보드 요약용 읽기 전용 집계입니다.
    long countByStatus(ReportStatus status);

    // 이미 신고한 대상인지 확인 (중복 신고 방지)
    boolean existsByReporterIdAndTargetIdAndStatusIn(
            Long reporterId, Long targetId, java.util.Collection<ReportStatus> statuses);

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

    // 기각된 신고 단건 조회
    Optional<Report> findTopByReporterIdAndTargetIdAndStatusOrderByProcessedAtDesc(
            Long reporterId, Long targetId, ReportStatus status);

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

    // 특정 postId에 PENDING 상태 신고가 있는지 확인
    // SQL: SELECT EXISTS (
    //        SELECT 1 FROM reports
    //        WHERE target_id = ? AND status = 'PENDING'
    //      )
    // targetId: 신고 대상 엔티티 ID (여기서는 postId)
    // ReportStatus.PENDING: 아직 관리자가 처리하지 않은 신고
    boolean existsByTargetIdAndStatus(Long targetId, ReportStatus status);

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Report r
        SET r.status = 'ACCEPTED',
            r.adminId = :adminId,
            r.processedAt = CURRENT_TIMESTAMP
        WHERE r.id = :reportId
          AND r.status = 'PENDING'
        """)
    int acceptIfPending(@Param("reportId") Long reportId, @Param("adminId") Long adminId);

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Report r
        SET r.status = 'REJECTED',
            r.adminId = :adminId,
            r.processedAt = CURRENT_TIMESTAMP
        WHERE r.id = :reportId
          AND r.status = 'PENDING'
        """)
    int rejectIfPending(@Param("reportId") Long reportId, @Param("adminId") Long adminId);
}
