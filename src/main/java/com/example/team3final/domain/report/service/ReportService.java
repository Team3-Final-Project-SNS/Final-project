package com.example.team3final.domain.report.service;

import com.example.team3final.domain.report.dto.request.CreateReportRequestDto;
import com.example.team3final.domain.report.dto.response.CreateReportResponseDto;
import com.example.team3final.domain.report.entity.Report;
import com.example.team3final.domain.report.enums.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReportService {

    // 신고 접수
    CreateReportResponseDto createReport(Long reporterId, CreateReportRequestDto request);

    // 신고 채택 - 관리자 호출용
    void acceptReport(Long reportId, Long adminId);

    // 신고 기각 - 관리자 호출용
    void rejectReport(Long reportId, Long adminId);

    // 신고 단건 조회
    Report getReportById(Long reportId);

    // 신고 목록 조회
    Page<Report> getReportsForAdmin(ReportStatus status, Pageable pageable);

    // 해당 게시글에 PENDING 상태 신고가 존재하는지 반환
    boolean existsPendingReport(Long postId);
}
