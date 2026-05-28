package com.example.team3final.domain.inquiry.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.inquiry.dto.request.CreateInquiryRequestDto;
import com.example.team3final.domain.inquiry.dto.response.CreateInquiryResponseDto;
import com.example.team3final.domain.inquiry.dto.response.GetAllInquiriesResponseDto;
import com.example.team3final.domain.inquiry.dto.response.GetOneInquiryResponseDto;
import com.example.team3final.domain.inquiry.service.InquiryService;
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
@RequestMapping("/api/v1/inquiries")
@RequiredArgsConstructor
public class InquiryController {

    private final InquiryService inquiryService;

    // 고객 문의 접수
    @PostMapping
    public ResponseEntity<ApiResponseDto<CreateInquiryResponseDto>> createInquiry(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody CreateInquiryRequestDto request
            ) {

        // jwt 토큰에서 검증된 userId 추출 (위변조 불가)
        Long userId = userDetails.getUserId();

        CreateInquiryResponseDto response = inquiryService.createInquiry(userId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED).body(ApiResponseDto.success(response));
    }

    // 내 문의 상세(답변포함) 조회
    @GetMapping("/{inquiryId}")
    public ResponseEntity<ApiResponseDto<GetOneInquiryResponseDto>> getOneInquiry(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long inquiryId
    ) {
        Long userId = userDetails.getUserId();

        GetOneInquiryResponseDto response = inquiryService.getOneInquiry(userId, inquiryId);

        return ResponseEntity.ok(ApiResponseDto.success(response));
    }

    // 내 문의 목록 조회
    @GetMapping("/me")
    public ResponseEntity<ApiResponseDto<PageResponseDto<GetAllInquiriesResponseDto>>> getAllInquiries(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long userId = userDetails.getUserId(); // jwt 토큰에서 userId 추철

        // PageRequest.of(page, size): Pageable 구현체 생성
        Pageable pageable = PageRequest.of(page, size);

        PageResponseDto<GetAllInquiriesResponseDto> response = inquiryService.getAllInquiries(userId, pageable);

        return ResponseEntity.ok(ApiResponseDto.success(response));
    }
}
