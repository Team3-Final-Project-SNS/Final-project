package com.example.team3final.domain.auth.service;

public interface OtpService {

    // 인증번호 발송
    void sendOtp(String to, String otpCode);
}
