package com.example.team3final.domain.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class VerifyPaymentRequestDto {

    // PortOne SDK가 결제 완료 후 프론트에 돌려준 결제 고유 ID
    // 이 값으로 서버가 PortOne API에 "이 결제 실제로 됐어?" 물어봄
    @NotBlank(message = "imp_uid는 필수입니다.")
    private String impUid;
}
