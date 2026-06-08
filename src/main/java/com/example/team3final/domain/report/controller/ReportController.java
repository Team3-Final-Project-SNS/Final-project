package com.example.team3final.domain.report.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.domain.report.dto.request.CreateReportRequestDto;
import com.example.team3final.domain.report.dto.response.CreateReportResponseDto;
import com.example.team3final.domain.report.service.ReportService;
import com.example.team3final.domain.user.service.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
}
