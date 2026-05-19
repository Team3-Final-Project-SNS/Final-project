package com.example.team3final.domain.auth.service;

import com.example.team3final.domain.auth.dto.request.LoginRequestDto;
import com.example.team3final.domain.auth.dto.request.OtpRequestDto;
import com.example.team3final.domain.auth.dto.response.LoginResponseDto;
import com.example.team3final.domain.auth.dto.response.OtpResponseDto;
import jakarta.servlet.http.HttpServletResponse;

public interface AuthService {

    // 회원가입을 위한 인증 번호 생성 및 발송
    OtpResponseDto sendEmailOtp(OtpRequestDto request);

    // 로그인 - Refresh Token을 응답 쿠키에 직접 세팅하므로 HttpServletResponse 필요
    LoginResponseDto login(LoginRequestDto request, HttpServletResponse response);
}
