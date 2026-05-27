package com.example.team3final.domain.admin.report.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.admin.report.dto.request.AdminProcessReportRequestDto;
import com.example.team3final.domain.admin.report.dto.response.AdminGetReportResponseDto;
import com.example.team3final.domain.admin.report.dto.response.AdminProcessReportResponseDto;
import com.example.team3final.domain.admin.report.service.AdminReportService;
import com.example.team3final.domain.admin.security.AdminDetailsImpl;
import com.example.team3final.domain.report.enums.ReportStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/reports")
public class AdminReportController {

    private final AdminReportService adminReportService;

    // 신고 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponseDto<PageResponseDto<AdminGetReportResponseDto>>> getReports(
            @AuthenticationPrincipal AdminDetailsImpl adminDetails,
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long adminId = adminDetails.getAdminId();
        Pageable pageable = PageRequest.of(page, size);

        return ResponseEntity.ok(ApiResponseDto.success(adminReportService.getReports(adminId, status, pageable)));
    }

    // 신고 처리
    @PatchMapping("/{reportId}/process")
    public ResponseEntity<ApiResponseDto<AdminProcessReportResponseDto>> processReport(
            @AuthenticationPrincipal AdminDetailsImpl adminDetails,
            @PathVariable Long reportId,
            @Valid @RequestBody AdminProcessReportRequestDto request) {
        Long adminId = adminDetails.getAdminId();
        return ResponseEntity.ok(ApiResponseDto.success(adminReportService.processReport(adminId, reportId, request)));
    }
}
