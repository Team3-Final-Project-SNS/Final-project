package com.example.team3final.domain.report.repository;

import com.example.team3final.domain.report.entity.Report;
import com.example.team3final.domain.report.enums.ReportTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    // 내 신고 내역 조회 (최신순 페이징)
    Page<Report> findByReporterIdOrderByCreatedAtDesc(Long reporterId, Pageable pageable);

    // 특정 신고 조회 (본인 신고만)
    Optional<Report> findByIdAndReporterId(Long id, Long reporterId);

    // 이미 신고한 대상인지 확인 (중복 신고 방지)
    boolean existsByReporterIdAndTargetTypeAndTargetId(
            Long reporterId, ReportTargetType targetType, Long targetId);

    // 10일 이내 동일 대상 재신고 확인 (기각된 신고 재신고 제한)
    boolean existsByReporterIdAndTargetTypeAndTargetIdAndCreatedAtAfter(
            Long reporterId, ReportTargetType targetType, Long targetId, LocalDateTime after);
}
