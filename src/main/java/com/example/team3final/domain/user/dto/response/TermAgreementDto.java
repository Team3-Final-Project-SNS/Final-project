package com.example.team3final.domain.user.dto.response;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// 약관 동의 항목 하나를 담는 DTO
// 회원가입 요청 안에 리스트로 포함됨
public record TermAgreementDto(

        // 약관 버전 식별자 (예: "v1.0-service", "v1.0-privacy")
        @NotBlank(message = "약관 버전은 필수입니다.")
        String termVersion,

        // 동의 여부
        @NotNull(message = "약관 동의 여부는 필수입니다.")
        Boolean agreed
) {}