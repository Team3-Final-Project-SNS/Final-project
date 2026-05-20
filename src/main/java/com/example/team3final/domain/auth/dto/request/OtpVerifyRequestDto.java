package com.example.team3final.domain.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpVerifyRequestDto {

        // OTP 발송 시 사용한 이메일 (동일한 이메일이어야 함)
        @NotBlank(message = "이메일은 필수 입니다.")
        @Email(message = "이메일 형식이 아닙니다.")
        private String email;

        // 서버가 생성한 6자리 숫자 OTP 코드
        @NotBlank(message = "인증번호는 필수입니다.")
        @Pattern(regexp = "^[0-9]{6}$", message = "인증번호는 6자리 숫자여야 합니다.")
        private String otpCode;
}