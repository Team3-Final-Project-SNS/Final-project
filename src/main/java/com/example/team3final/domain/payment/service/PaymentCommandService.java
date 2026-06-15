package com.example.team3final.domain.payment.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.payment.dto.request.CreatePaymentRequestDto;
import com.example.team3final.domain.payment.dto.request.VerifyPaymentRequestDto;
import com.example.team3final.domain.payment.dto.response.CancelPaymentResponseDto;
import com.example.team3final.domain.payment.dto.response.CreatePaymentResponseDto;
import com.example.team3final.domain.payment.dto.response.GetPaymentResponseDto;
import com.example.team3final.domain.payment.dto.response.VerifyPaymentResponseDto;
import org.springframework.data.domain.Pageable;

// Payment 도메인의 결제 생성/검증/조회/취소/실패 처리 기능을 담당하는 서비스
public interface PaymentCommandService {

    // 결제 준비 - merchant_uid 채번 + READY 상태로 DB 저장
    CreatePaymentResponseDto createPayment(Long userId, CreatePaymentRequestDto request);

    // 결제 완료 검증 - PortOne API 호출해서 금액 검증 후 포인트 지급
    VerifyPaymentResponseDto verifyPayment(
            Long userId,
            Long paymentId,
            VerifyPaymentRequestDto request
    );

    // 결제 내역 조회 - 내 결제 목록 최신순
    PageResponseDto<GetPaymentResponseDto> getPayments(Long userId, Pageable pageable);

    // 결제 취소 — PortOne 취소 API 호출 + paidPoint 회수
    CancelPaymentResponseDto cancelPayment(Long userId, Long paymentId);

    // 결제 실패 처리 — 프론트가 결제창 취소/실패 시 호출
    void failPayment(Long userId, Long paymentId);
}
