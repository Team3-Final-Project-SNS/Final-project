package com.example.team3final.domain.payment.dto.response;

import com.example.team3final.domain.payment.entity.Payment;
import com.example.team3final.domain.payment.enums.PaymentStatus;

import java.time.LocalDateTime;

public record CancelPaymentResponseDto(
        Long paymentId,
        PaymentStatus status,     // CANCELLED
        int refundedAmount,       // 실제 환불된 금액(원) - 사용한 포인트 제외
        LocalDateTime cancelledAt
) {
    public static CancelPaymentResponseDto of(Payment payment, int refundedAmount) {
        return new CancelPaymentResponseDto(
                payment.getId(),
                payment.getStatus(),
                refundedAmount,
                payment.getCancelledAt()
        );
    }
}
