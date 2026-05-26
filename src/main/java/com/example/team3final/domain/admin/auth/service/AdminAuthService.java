package com.example.team3final.domain.admin.auth.service;

import com.example.team3final.domain.admin.auth.dto.request.AdminLoginRequestDto;
import com.example.team3final.domain.admin.auth.dto.response.AdminLoginResponseDto;

public interface AdminAuthService {

    AdminLoginResponseDto login(AdminLoginRequestDto requestDto);
}
