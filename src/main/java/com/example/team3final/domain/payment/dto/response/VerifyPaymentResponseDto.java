package com.example.team3final.domain.payment.dto.response;

import com.example.team3final.domain.payment.entity.Payment;
import com.example.team3final.domain.payment.enums.PaymentStatus;

import java.time.LocalDateTime;

public record VerifyPaymentResponseDto(
        Long paymentId,
        String impUid,            // PortOne 결제 고유 ID (프론트가 보낸 값 그대로 응답)
        int chargePoint,          // 충전된 포인트
        int amount,               // 결제 금액(원)
        PaymentStatus status,     // PAID
        int balanceAfter,         // 결제 후 총 포인트 잔액 (freePoint + paidPoint)
        LocalDateTime completedAt
) {
    public static VerifyPaymentResponseDto of(Payment payment, String impUid, int balanceAfter) {
        return new VerifyPaymentResponseDto(
                payment.getId(),
                impUid,
                payment.getChargePoint(),
                payment.getAmount(),
                payment.getStatus(),
                balanceAfter,
                payment.getCompletedAt()
        );
    }
}
