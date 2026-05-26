package com.example.team3final.domain.report.repository;

import com.example.team3final.domain.report.entity.Report;
import com.example.team3final.domain.report.enums.ReportStatus;
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
    boolean existsByReporterIdAndTargetId(Long reporterId, Long targetId);

    // 10일 이내 동일 대상 재신고 확인
    boolean existsByReporterIdAndTargetIdAndCreatedAtAfter(
            Long reporterId, Long targetId, LocalDateTime after);

    // 관리자용 신고 목록 조회 - 상태 필터
    Page<Report> findByStatus(ReportStatus status, Pageable pageable);

    // 피신고자 채택 횟수 조회 (제재 정책용)
    int countByTargetIdAndStatus(Long targetId, ReportStatus status);
}
