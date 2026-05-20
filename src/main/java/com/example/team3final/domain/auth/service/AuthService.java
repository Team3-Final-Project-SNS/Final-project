package com.example.team3final.domain.auth.service;

import com.example.team3final.domain.auth.dto.request.LoginRequestDto;
import com.example.team3final.domain.auth.dto.request.OtpRequestDto;
import com.example.team3final.domain.auth.dto.request.OtpVerifyRequestDto;
import com.example.team3final.domain.auth.dto.response.LoginResponseDto;
import com.example.team3final.domain.auth.dto.response.OtpResponseDto;
import com.example.team3final.domain.auth.dto.response.OtpVerifyResponseDto;
import com.example.team3final.domain.auth.dto.response.TokenResponseDto;
import jakarta.servlet.http.HttpServletResponse;

public interface AuthService {

    // 회원가입을 위한 인증 번호 생성 및 발송
    OtpResponseDto sendEmailOtp(OtpRequestDto request);

    // 회원가입을 위한 인증번호 검증 - 성공시 signup token을 httpOnly 쿠키로 발급
    OtpVerifyResponseDto verifyEmailOtp(OtpVerifyRequestDto request, HttpServletResponse response);

    // 로그인 - Refresh Token을 응답 쿠키에 직접 세팅하므로 HttpServletResponse 필요
    LoginResponseDto login(LoginRequestDto request, HttpServletResponse response);

    // 토큰 재발급
    TokenResponseDto refresh(String refreshToken, HttpServletResponse response);

    // 로그아웃 - 리프레쉬 토큰 쿠키 만료 처리
    void logout(String refreshToken, HttpServletResponse response);
}
