package com.example.team3final.domain.admin.auth.controller;

import com.example.team3final.common.dto.response.ApiResponseDto;
import com.example.team3final.domain.admin.auth.dto.request.AdminLoginRequestDto;
import com.example.team3final.domain.admin.auth.dto.response.AdminLoginResponseDto;
import com.example.team3final.domain.admin.auth.service.AdminAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    // 관리자 로그인
    @PostMapping("/auth/login")
    public ResponseEntity<ApiResponseDto<AdminLoginResponseDto>> login(
            @Valid @RequestBody AdminLoginRequestDto request) {

        return ResponseEntity.ok(ApiResponseDto.success(adminAuthService.login(request)));
    }
}
