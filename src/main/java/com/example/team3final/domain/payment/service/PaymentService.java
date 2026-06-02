package com.example.team3final.domain.payment.service;

import com.example.team3final.domain.payment.dto.request.CreatePaymentRequestDto;
import com.example.team3final.domain.payment.dto.response.CreatePaymentResponseDto;

public interface PaymentService {

    // 결제 준비 - merchant_uid 채번 + READY 상태로 DB 저장
    CreatePaymentResponseDto createPayment(Long userId, CreatePaymentRequestDto request);
}
