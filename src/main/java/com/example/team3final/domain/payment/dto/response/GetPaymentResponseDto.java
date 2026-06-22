package com.example.team3final.domain.payment.dto.response;

import com.example.team3final.domain.payment.entity.Payment;
import com.example.team3final.domain.payment.enums.PaymentStatus;

import java.time.LocalDateTime;

public record GetPaymentResponseDto(
        Long paymentId,
        int chargePoint,
        int amount,
        String payMethod,
        PaymentStatus status,
        LocalDateTime createdAt,
        LocalDateTime completedAt   // PAID가 아니면 null
) {
    public static GetPaymentResponseDto from(Payment payment) {
        return new GetPaymentResponseDto(
                payment.getId(),
                payment.getChargePoint(),
                payment.getAmount(),
                payment.getPayMethod(),
                payment.getStatus(),
                payment.getCreatedAt(),
                payment.getCompletedAt()
        );
    }
}
