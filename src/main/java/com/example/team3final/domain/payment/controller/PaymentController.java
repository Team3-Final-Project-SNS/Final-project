package com.example.team3final.domain.payment.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.domain.payment.dto.request.CreatePaymentRequestDto;
import com.example.team3final.domain.payment.dto.request.VerifyPaymentRequestDto;
import com.example.team3final.domain.payment.dto.response.CreatePaymentResponseDto;
import com.example.team3final.domain.payment.dto.response.VerifyPaymentResponseDto;
import com.example.team3final.domain.payment.service.PaymentService;
import com.example.team3final.domain.user.service.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * 결제 준비 — POST /api/v1/payments
     *
     * 프론트 흐름:
     * 1. 이 API 호출 → merchantUid + paymentId 받음
     * 2. PortOne SDK에 merchantUid 전달해서 실제 결제 진행
     * 3. 결제 완료 후 verifyPayment API 호출
     */
    @PostMapping
    public ResponseEntity<ApiResponseDto<CreatePaymentResponseDto>> createPayment(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody CreatePaymentRequestDto request
    ) {
        Long userId = userDetails.getUserId();
        CreatePaymentResponseDto response = paymentService.createPayment(userId, request);

        // 201 Created - 새로운 결제 준비 건이 생성됐으므로
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(response));
    }

    // 결제 완료 검증
    @PostMapping("/{paymentId}/verify")
    public ResponseEntity<ApiResponseDto<VerifyPaymentResponseDto>> verifyPayment(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long paymentId,
            @Valid @RequestBody VerifyPaymentRequestDto request
    ) {
       Long userId = userDetails.getUserId();
       return ResponseEntity.ok(
               ApiResponseDto.success(
                       paymentService.verifyPayment(userId, paymentId, request)
               )
       );
    }
}
