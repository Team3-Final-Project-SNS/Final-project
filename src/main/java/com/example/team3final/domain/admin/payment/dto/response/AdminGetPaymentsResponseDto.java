package com.example.team3final.domain.admin.payment.dto.response;

import com.example.team3final.domain.payment.entity.Payment;
import com.example.team3final.domain.payment.enums.ChargePackage;
import com.example.team3final.domain.payment.enums.PaymentStatus;

import java.time.LocalDateTime;

public record AdminGetPaymentsResponseDto(

        Long paymentId,                 // 결제 PK
        Long userId,                    // 결제를 요청한 사용자 ID
        String merchantUid,             // 주문번호
        ChargePackage chargePackage,    // 충전 패키지
        int chargePoint,                // 충전 포인트
        int amount,                     // 실제 결제 금액
        String payMethod,               // 결제 수단
        PaymentStatus status,           // 결제 상태
        String cancelReason,            // 결제 취소 사유
        String failReason,              // 결제 실패 사유
        LocalDateTime createdAt,        // 결제 row 생성 시각
        LocalDateTime completedAt,      // 결제 완료 시각
        LocalDateTime cancelledAt       // 결제 취소 시각
) {
    public static AdminGetPaymentsResponseDto from(Payment payment) {
        return new AdminGetPaymentsResponseDto(
                payment.getId(),
                payment.getUserId(),
                payment.getMerchantUid(),
                payment.getChargePackage(),
                payment.getChargePoint(),
                payment.getAmount(),
                payment.getPayMethod(),
                payment.getStatus(),
                payment.getCancelReason(),
                payment.getFailReason(),
                payment.getCreatedAt(),
                payment.getCompletedAt(),
                payment.getCancelledAt()
        );
    }
}
