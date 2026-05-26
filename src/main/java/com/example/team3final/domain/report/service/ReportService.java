package com.example.team3final.domain.report.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.report.dto.request.CreateReportRequestDto;
import com.example.team3final.domain.report.dto.response.CreateReportResponseDto;
import com.example.team3final.domain.report.dto.response.DeleteReportResponseDto;
import com.example.team3final.domain.report.dto.response.GetMyReportsResponseDto;
import org.springframework.data.domain.Pageable;

public interface ReportService {

    // 신고 접수
    CreateReportResponseDto createReport(Long reporterId, CreateReportRequestDto request);

    // 내 신고 내역 조회
    PageResponseDto<GetMyReportsResponseDto> getMyReports(Long reporterId, Pageable pageable);

    // 신고 취소
    DeleteReportResponseDto deleteReport(Long reporterId, Long reportId);
}
