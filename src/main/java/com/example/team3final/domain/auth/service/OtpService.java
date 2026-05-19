package com.example.team3final.domain.auth.service;

public interface OtpService {

    // 인증번호 이메일 생성
    void sendOtp(String to, String otpCode);
}
