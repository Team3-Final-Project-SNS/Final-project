package com.example.team3final.domain.admin.report.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.admin.report.dto.request.AdminProcessReportRequestDto;
import com.example.team3final.domain.admin.report.dto.response.AdminGetReportDetailResponseDto;
import com.example.team3final.domain.admin.report.dto.response.AdminGetReportsResponseDto;
import com.example.team3final.domain.admin.report.dto.response.AdminProcessReportResponseDto;
import com.example.team3final.domain.report.enums.ReportStatus;
import org.springframework.data.domain.Pageable;

public interface AdminReportService {

    // 신고 목록 조회
    PageResponseDto<AdminGetReportsResponseDto> getReports(Long adminId, ReportStatus status, Pageable pageable);


    // 신고 처리 (채택 / 기각)
    AdminProcessReportResponseDto processReport(Long adminId, Long reportId, AdminProcessReportRequestDto requestDto);

    // 신고 상세 조회
    AdminGetReportDetailResponseDto getReport(Long adminId, Long reportId);
}
