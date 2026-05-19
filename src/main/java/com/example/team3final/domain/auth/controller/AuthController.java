package com.example.team3final.domain.auth.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.domain.auth.dto.OtpRequestDto;
import com.example.team3final.domain.auth.dto.OtpResponseDto;
import com.example.team3final.domain.auth.service.AuthService;
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
        OtpResponseDto response = authService.sendEmailOtp(request);
        return ResponseEntity.ok(ApiResponseDto.success(response));
    }
}
