package com.example.team3final.domain.admin.inquiry.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.admin.inquiry.dto.request.AdminCreateInquiryRequestDto;
import com.example.team3final.domain.admin.inquiry.dto.response.AdminCreateInquiryResponseDto;
import com.example.team3final.domain.admin.inquiry.dto.response.AdminGetInquiriesResponseDto;
import com.example.team3final.domain.admin.inquiry.dto.response.AdminGetInquiryResponseDto;
import com.example.team3final.domain.admin.inquiry.service.AdminInquiryService;
import com.example.team3final.domain.admin.security.AdminDetailsImpl;
import com.example.team3final.domain.inquiry.enums.InquiryAnswerStatus;
import com.example.team3final.domain.inquiry.enums.InquiryType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class AdminInquiryController {

    private final AdminInquiryService adminInquiryService;

    // 고객 문의 상세 조회
    @GetMapping("/inquiries/{inquiryId}")
    public ResponseEntity<ApiResponseDto<AdminGetInquiryResponseDto>> getInquiry(
            @AuthenticationPrincipal AdminDetailsImpl adminDetails,
            @PathVariable Long inquiryId) {

        Long adminId = adminDetails.getAdminId();
        return ResponseEntity.ok(ApiResponseDto.success(adminInquiryService.getInquiry(adminId, inquiryId)));
    }

    // 고객 문의 목록 조회
    @GetMapping("/inquiries")
    public ResponseEntity<ApiResponseDto<PageResponseDto<AdminGetInquiriesResponseDto>>> getInquiries(
            @AuthenticationPrincipal AdminDetailsImpl adminDetails,
            @RequestParam(required = false) InquiryAnswerStatus status,
            @RequestParam(required = false) InquiryType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long adminId = adminDetails.getAdminId();
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponseDto.success(adminInquiryService.getInquiries(adminId, status, type, pageable)));
    }

    // 고객 문의 답변
    @PostMapping("/inquiries/{inquiryId}/answers")
    public ResponseEntity<ApiResponseDto<AdminCreateInquiryResponseDto>> createAnswer(
            @AuthenticationPrincipal AdminDetailsImpl adminDetails,
            @PathVariable Long inquiryId,
            @Valid @RequestBody AdminCreateInquiryRequestDto requestDto) {

        Long adminId = adminDetails.getAdminId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(adminInquiryService.createAnswer(adminId, inquiryId, requestDto)));
    }
}
