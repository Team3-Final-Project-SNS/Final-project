package com.example.team3final.domain.payment.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentStatus {

    READY("결제 준비 - PortOne SDK 호출 전"),
    PAID("결제 완료 및 포인트 지급"),
    CANCELLED("결제 취소"),
    FAILED("결제 실패");

    private final String description;

    /**
     * 이미 종결(완료/취소)된 상태인지 여부.
     * - 검증/취소 로직에서 "이미 처리된 결제(PAY_005)" 판단에 사용.
     * - 값 하나로 판단되는 분류 로직이라 enum 에 둔다.
     */
    public boolean isFinalized() {
        return this == PAID || this == CANCELLED;
    }
}
