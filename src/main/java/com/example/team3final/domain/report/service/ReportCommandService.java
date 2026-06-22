package com.example.team3final.domain.report.service;

import com.example.team3final.domain.report.dto.request.CreateReportRequestDto;
import com.example.team3final.domain.report.dto.response.CreateReportResponseDto;

// Report 도메인의 신고 생성 기능을 담당하는 서비스
public interface ReportCommandService {

    // 신고 접수
    CreateReportResponseDto createReport(Long reporterId, CreateReportRequestDto request);

}
