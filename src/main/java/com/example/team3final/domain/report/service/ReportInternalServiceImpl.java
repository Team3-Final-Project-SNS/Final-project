package com.example.team3final.domain.report.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.ReportException;
import com.example.team3final.domain.report.entity.Report;
import com.example.team3final.domain.report.enums.ReportStatus;
import com.example.team3final.domain.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Report 도메인의 타 도메인/관리자 호출용 내부 조회 기능을 제공하는 서비스
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportInternalServiceImpl implements ReportInternalService {

    private final ReportRepository reportRepository;

    @Override
    public Report getReportById(Long reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new ReportException(ErrorCode.REPORT_NOT_FOUND));
    }

    @Override
    public Page<Report> getReportsForAdmin(ReportStatus status, Pageable pageable) {
        return reportRepository.findAllByStatusFilter(status, pageable);
    }

    // 특정 postId에 PENDING 상태 신고가 있는지 확인
    @Override
    public boolean existsPendingReport(Long postId) {
        // reportRepository에 postId + PENDING 상태 조합으로 존재 여부 조회
        // EXISTS 쿼리 → 있으면 true, 없으면 false 반환
        return reportRepository.existsByTargetIdAndStatus(postId, ReportStatus.PENDING);
    }
}
