package com.example.team3final.domain.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class CreatePaymentRequestDto {

    // 패키지 포인트 - ChargePackage.fromPoint()로 검증 (3000/5000/10000/20000만 허용)
    @NotNull(message = "충전 포인트는 필수입니다.")
    private Integer chargePoint;

    // 결제 수단
    @NotBlank(message = "결제 수단은 필수입니다.")
    private String payMethod;
}
