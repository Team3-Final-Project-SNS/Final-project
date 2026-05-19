package com.example.team3final.domain.auth.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.domain.auth.dto.request.LoginRequestDto;
import com.example.team3final.domain.auth.dto.request.OtpRequestDto;
import com.example.team3final.domain.auth.dto.response.LoginResponseDto;
import com.example.team3final.domain.auth.dto.response.OtpResponseDto;
import com.example.team3final.domain.auth.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/v1/auth")
public class AuthController {

    private final AuthService authService;

    // otp 인증번호 이메일 발송
    @PostMapping("/email/otp")
    public ResponseEntity<ApiResponseDto<OtpResponseDto>> sendEmailOtp(
            @RequestBody @Valid OtpRequestDto request) {
        return ResponseEntity.ok(ApiResponseDto.success(authService.sendEmailOtp(request)));
    }

    // 로그인
    @PostMapping("/login")
    public ResponseEntity<ApiResponseDto<LoginResponseDto>> login(
            @RequestBody @Valid LoginRequestDto request,
            HttpServletResponse response) {
        // response를 넘기는 이유: Refresh Token 쿠키를 서비스에서 직접 세팅하기 위해
        return ResponseEntity.ok(ApiResponseDto.success(authService.login(request, response)));
    }
}
