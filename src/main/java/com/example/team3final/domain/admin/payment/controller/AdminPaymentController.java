package com.example.team3final.domain.admin.payment.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.admin.payment.dto.response.AdminGetPaymentsResponseDto;
import com.example.team3final.domain.admin.payment.service.AdminPaymentService;
import com.example.team3final.domain.admin.security.AdminDetailsImpl;
import com.example.team3final.domain.payment.enums.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class AdminPaymentController {

    private final AdminPaymentService adminPaymentService;

    // 관리자 결제내역 목록 조회
    @GetMapping("/payments")
    public ResponseEntity<ApiResponseDto<PageResponseDto<AdminGetPaymentsResponseDto>>> getPayments(
            @AuthenticationPrincipal AdminDetailsImpl adminDetails,
            // 특정 유저의 결제내역만 조회하고 싶을 때 사용.
            // 없으면 전체 유저 결제내역 조회.
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long adminId = adminDetails.getAdminId();
        // 너무 큰 size 요청으로 DB/서버 부하가 커지는 것을 방지.
        Pageable pageable = PageRequest.of(page, Math.min(size, 50));

        return ResponseEntity.ok(ApiResponseDto.success
                (adminPaymentService.getPayments(adminId, userId, status, pageable)));
    }
}
