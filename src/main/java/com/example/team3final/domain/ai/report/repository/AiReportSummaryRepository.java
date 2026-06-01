package com.example.team3final.domain.ai.report.repository;

import com.example.team3final.domain.ai.report.entity.AiReportSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 신고 AI 분석 결과를 저장하고 조회하는 JPA Repository입니다.
 *
 * 같은 신고가 여러 번 분석될 수 있으므로 신고 ID 기준 최신 분석 결과를
 * 조회하는 메서드를 제공합니다.
 */
public interface AiReportSummaryRepository extends JpaRepository<AiReportSummary, Long> {

    Optional<AiReportSummary> findFirstByReportIdOrderByCreatedAtDesc(Long reportId);
}
