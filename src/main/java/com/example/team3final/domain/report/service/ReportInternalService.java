package com.example.team3final.domain.report.service;

import com.example.team3final.domain.report.entity.Report;
import com.example.team3final.domain.report.enums.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

// Report 도메인의 타 도메인/관리자 호출용 내부 조회 기능을 제공하는 서비스
public interface ReportInternalService {

    // 신고 단건 조회
    Report getReportById(Long reportId);

    // 신고 목록 조회
    Page<Report> getReportsForAdmin(ReportStatus status, Pageable pageable);

    // 해당 게시글에 PENDING 상태 신고가 존재하는지 반환
    boolean existsPendingReport(Long postId);
}
