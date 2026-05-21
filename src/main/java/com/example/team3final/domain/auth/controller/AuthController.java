package com.example.team3final.domain.auth.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.domain.auth.dto.request.*;
import com.example.team3final.domain.auth.dto.response.*;
import com.example.team3final.domain.auth.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

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

    // otp 검증 및 signup_token 발급
    @PostMapping("/email/otp/verify")
    public ResponseEntity<ApiResponseDto<OtpVerifyResponseDto>> verifyEmailOtp(
            @RequestBody @Valid OtpVerifyRequestDto request,
            HttpServletResponse response) { // signup_token 쿠키를 응답에 담기 위해 필요
        return ResponseEntity.ok(
                ApiResponseDto.success(authService.verifyEmailOtp(request, response))
        );
    }

    // 회원가입
    @PostMapping("/signup")
    public ResponseEntity<ApiResponseDto<SignupResponseDto>> signup(
            @RequestBody @Valid SignupRequestDto request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        return ResponseEntity
                .status(HttpStatus.CREATED) // 명세서: 201 Created
                .body(ApiResponseDto.success(
                        authService.signup(request, httpRequest, httpResponse)
                ));
    }

    // 로그인
    @PostMapping("/login")
    public ResponseEntity<ApiResponseDto<LoginResponseDto>> login(
            @RequestBody @Valid LoginRequestDto request,
            HttpServletResponse response) {
        // response를 넘기는 이유: Refresh Token 쿠키를 서비스에서 직접 세팅하기 위해
        return ResponseEntity.ok(ApiResponseDto.success(authService.login(request, response)));
    }

    // 토큰 재발급
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponseDto<TokenResponseDto>> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {

        // 쿠키에서 refresh_token 꺼내기
        String refreshToken = extractRefreshTokenFromCookie(request);
        return ResponseEntity.ok(ApiResponseDto.success(authService.refresh(refreshToken, response)));
    }

    // 로그아웃
    @PostMapping("/logout")
    public ResponseEntity<ApiResponseDto<Void>> logout(
            HttpServletRequest request,
            HttpServletResponse response) {

        // 쿠키에서 refresh_token 꺼내기
        String refreshToken = extractRefreshTokenFromCookie(request);
        authService.logout(refreshToken, response);
        return ResponseEntity.ok(ApiResponseDto.successWithNoContent());
    }

    // 쿠키 배열에서 refresh_token 값을 찾아 반환하는 헬퍼 메서드
    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;

        return Arrays.stream(request.getCookies())
                .filter(cookie -> "refresh_token".equals(cookie.getName()))
                .findFirst()
                .map(Cookie::getValue)
                .orElse(null);
    }
}
