package com.example.team3final.domain.admin.auth.service;

import com.example.team3final.common.exception.AdminException;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.jwt.JwtProvider;
import com.example.team3final.domain.admin.auth.dto.request.AdminLoginRequestDto;
import com.example.team3final.domain.admin.auth.dto.response.AdminLoginResponseDto;
import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuthServiceImpl implements AdminAuthService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Override
    public AdminLoginResponseDto login(AdminLoginRequestDto requestDto) {

        // 이메일로 Admin 조회
        Admin admin = adminRepository.findByEmail(requestDto.getEmail())
                .orElseThrow( () -> new AdminException(ErrorCode.ADMIN_NOT_FOUND));

        // 비밀번호 검증
        if (!passwordEncoder.matches(requestDto.getPassword(), admin.getPassword())) {
            throw new AdminException(ErrorCode.ADMIN_LOGIN_FAIL);
        }

        // 계정 활성화 여부 체크
        if (!admin.isActiveAdmin()) {
            throw new AdminException(ErrorCode.ADMIN_ACCOUNT_INACTIVE);
        }

        // Admin Access Token 발급 (15분 만료, Refresh Token 미적용)
        String adminAccessToken = jwtProvider.generateAdminAccessToken(admin.getEmail());

        // 응답 DTO로 반환
        return AdminLoginResponseDto.of(admin, adminAccessToken);
    }
}
