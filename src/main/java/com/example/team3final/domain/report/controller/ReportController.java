package com.example.team3final.domain.report.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.report.dto.request.CreateReportRequestDto;
import com.example.team3final.domain.report.dto.response.CreateReportResponseDto;
import com.example.team3final.domain.report.dto.response.DeleteReportResponseDto;
import com.example.team3final.domain.report.dto.response.GetMyReportsResponseDto;
import com.example.team3final.domain.report.service.ReportService;
import com.example.team3final.domain.user.service.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    // 신고 접수
    @PostMapping
    public ResponseEntity<ApiResponseDto<CreateReportResponseDto>> createReport(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody CreateReportRequestDto request) {
        Long reporterId = userDetails.getUserId();
        CreateReportResponseDto response = reportService.createReport(reporterId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDto.success(response));
    }

    // 내 신고 내역 조회
    @GetMapping("/me")
    public ResponseEntity<ApiResponseDto<PageResponseDto<GetMyReportsResponseDto>>> getMyReports(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long reporterId = userDetails.getUserId();
        Pageable pageable = PageRequest.of(page, size);
        PageResponseDto<GetMyReportsResponseDto> response = reportService.getMyReports(reporterId, pageable);
        return ResponseEntity.ok(ApiResponseDto.success(response));
    }

    // 신고 취소
    @DeleteMapping("/{reportId}")
    public ResponseEntity<ApiResponseDto<DeleteReportResponseDto>> deleteReport(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long reportId
    ) {
        Long reporterId = userDetails.getUserId();
        DeleteReportResponseDto response = reportService.deleteReport(reporterId, reportId);
        return ResponseEntity.ok(ApiResponseDto.success(response));
    }
}
