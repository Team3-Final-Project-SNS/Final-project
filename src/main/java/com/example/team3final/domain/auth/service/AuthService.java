package com.example.team3final.domain.auth.service;

import com.example.team3final.domain.auth.dto.request.OtpRequestDto;
import com.example.team3final.domain.auth.dto.response.OtpResponseDto;

public interface AuthService {

    // 회원가입을 위한 인증 번호 생성 및 발송
    OtpResponseDto sendEmailOtp(OtpRequestDto request);
}
