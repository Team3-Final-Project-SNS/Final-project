package com.example.team3final.domain.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginRequestDto {

        // 이메일 유효성 검증
        @NotBlank(message = "이메일은 필수입니다")
        @Email(message = "이메일 형식이 아닙니다.")
        private String email;

        // 비밀번호: 빈값 거부
        @NotBlank(message = "비밀번호는 필수입니다.")
        private String password;
}