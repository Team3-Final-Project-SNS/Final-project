package com.example.team3final.domain.meet.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor // Jackson 역직렬화용 기본 생성자
public class QrScanRequestDto {

    @NotBlank(message = "QR 토큰은 필수입니다.")
    private String qrToken;
}
