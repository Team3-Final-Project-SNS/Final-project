package com.example.team3final.domain.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// ✅ 팀 컨벤션: RequestDto는 일반 class + @Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpRequestDto {

        // OTP를 발송할 학교 이메일 주소
        @NotBlank
        @Email
        private String email;
}