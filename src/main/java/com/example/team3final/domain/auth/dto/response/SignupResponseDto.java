package com.example.team3final.domain.auth.dto.response;

// 회원가입 성공 응답 DTO
// 규칙: Response DTO는 record 사용
public record SignupResponseDto(
        Long userId,
        String nickname,
        int point,           // 가입 즉시 10,000P 지급 확인용
        String accessToken   // 가입 후 바로 로그인 상태로 만들어줌
) {}