package com.example.team3final.domain.payment.service;

import com.example.team3final.domain.payment.dto.request.CreatePaymentRequestDto;
import com.example.team3final.domain.payment.dto.request.VerifyPaymentRequestDto;
import com.example.team3final.domain.payment.dto.response.CreatePaymentResponseDto;
import com.example.team3final.domain.payment.dto.response.VerifyPaymentResponseDto;

public interface PaymentService {

    // 결제 준비 - merchant_uid 채번 + READY 상태로 DB 저장
    CreatePaymentResponseDto createPayment(Long userId, CreatePaymentRequestDto request);

    // 결제 완료 검증 - PortOne API 호출해서 금액 검증 후 포인트 지급
    VerifyPaymentResponseDto verifyPayment(Long userId, Long paymentId,
                                           VerifyPaymentRequestDto request);
}
