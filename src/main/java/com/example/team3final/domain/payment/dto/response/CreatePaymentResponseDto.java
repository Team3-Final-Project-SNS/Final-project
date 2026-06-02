package com.example.team3final.domain.payment.dto.response;

import com.example.team3final.domain.payment.entity.Payment;
import com.example.team3final.domain.payment.enums.PaymentStatus;

import java.time.LocalDateTime;

public record CreatePaymentResponseDto(
        Long paymentId,         // 우리 DB의 결제 ID
        String merchantUid,     // PortOne에 전달할 주문번호
        int chargePoint,        // 충전될 포인트
        int amount,             // 결제 금액(원)
        PaymentStatus status,   // READY
        LocalDateTime createdAt
) {
    public static CreatePaymentResponseDto from(Payment payment) {
        return new CreatePaymentResponseDto(
                payment.getId(),
                payment.getMerchantUid(),
                payment.getChargePoint(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getCreatedAt()
        );
    }
}
