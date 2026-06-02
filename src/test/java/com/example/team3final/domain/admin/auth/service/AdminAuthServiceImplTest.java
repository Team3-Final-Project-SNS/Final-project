package com.example.team3final.domain.admin.auth.service;

import com.example.team3final.domain.admin.auth.dto.request.AdminLoginRequestDto;
import com.example.team3final.domain.admin.auth.dto.response.AdminLoginResponseDto;
import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.repository.AdminRepository;
import com.example.team3final.common.exception.AdminException;
import com.example.team3final.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceImplTest {

    @Mock
    private AdminRepository adminRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private com.example.team3final.common.jwt.JwtProvider jwtProvider;

    @InjectMocks
    private AdminAuthServiceImpl adminAuthService;

    @Test
    @DisplayName("관리자 로그인 - 성공")
    void login_Success() {
        // given
        AdminLoginRequestDto request = new AdminLoginRequestDto();
        ReflectionTestUtils.setField(request, "email", "admin@test.com");
        ReflectionTestUtils.setField(request, "password", "password");
        
        Admin admin = Admin.builder().email("admin@test.com").password("encodedPassword").build();
        ReflectionTestUtils.setField(admin, "isActive", true);
        ReflectionTestUtils.setField(admin, "role", com.example.team3final.domain.admin.enums.AdminRole.SUPER_ADMIN);
        
        given(adminRepository.findByEmail("admin@test.com")).willReturn(Optional.of(admin));
        given(passwordEncoder.matches("password", "encodedPassword")).willReturn(true);
        given(jwtProvider.generateAdminAccessToken("admin@test.com")).willReturn("token");

        // when
        AdminLoginResponseDto result = adminAuthService.login(request);

        // then
        assertNotNull(result);
    }

    @Test
    @DisplayName("관리자 로그인 - 실패 (비밀번호 불일치)")
    void login_Fail_WrongPassword() {
        // given
        AdminLoginRequestDto request = new AdminLoginRequestDto();
        ReflectionTestUtils.setField(request, "email", "admin@test.com");
        ReflectionTestUtils.setField(request, "password", "wrong");

        Admin admin = Admin.builder().email("admin@test.com").password("encodedPassword").build();
        given(adminRepository.findByEmail("admin@test.com")).willReturn(Optional.of(admin));
        given(passwordEncoder.matches("wrong", "encodedPassword")).willReturn(false);

        // when & then
        assertThrows(AdminException.class, () -> adminAuthService.login(request));
    }
}
