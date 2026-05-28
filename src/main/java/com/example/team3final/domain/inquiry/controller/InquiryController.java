package com.example.team3final.domain.inquiry.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.domain.inquiry.dto.request.CreateInquiryRequestDto;
import com.example.team3final.domain.inquiry.dto.response.CreateInquiryResponseDto;
import com.example.team3final.domain.inquiry.dto.response.GetInquiryResponseDto;
import com.example.team3final.domain.inquiry.service.InquiryService;
import com.example.team3final.domain.user.service.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
    public ResponseEntity<ApiResponseDto<GetInquiryResponseDto>> getInquiry(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long inquiryId
    ) {
        Long userId = userDetails.getUserId();

        GetInquiryResponseDto response = inquiryService.getInquiry(userId, inquiryId);

        return ResponseEntity.ok(ApiResponseDto.success(response));
    }
}
