package com.example.team3final.domain.auth.dto.response;

public record OtpVerifyResponseDto (

        // 이메일 도메인으로 찾은 학교 ID
        Long universityId,

        // 학교이름 - 화면에 00대학교 이메일 인증 완료" 표시용
        String universityName
) {}
