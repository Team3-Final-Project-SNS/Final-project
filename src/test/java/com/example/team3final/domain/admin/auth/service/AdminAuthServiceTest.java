package com.example.team3final.domain.admin.auth.service;

import com.example.team3final.common.jwt.JwtProvider;
import com.example.team3final.domain.admin.auth.dto.request.AdminLoginRequestDto;
import com.example.team3final.domain.admin.auth.dto.response.AdminLoginResponseDto;
import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.enums.AdminRole;
import com.example.team3final.domain.admin.repository.AdminRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceTest {

    @InjectMocks
    private AdminAuthServiceImpl adminAuthService;

    @Mock
    private AdminRepository adminRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("관리자 로그인 - 성공")
    void login_Success() {
        // given
        AdminLoginRequestDto request = new AdminLoginRequestDto();
        ReflectionTestUtils.setField(request, "email", "admin@test.com");
        ReflectionTestUtils.setField(request, "password", "password");
Admin admin = Admin.builder()
        .email("admin@test.com")
        .password("password")
        .name("admin")
        .role(AdminRole.SUPER_ADMIN)
        .build();
        ReflectionTestUtils.setField(admin, "id", 1L);

        given(adminRepository.findByEmail(anyString())).willReturn(Optional.of(admin));
        given(passwordEncoder.matches(anyString(), anyString())).willReturn(true);
        given(jwtProvider.generateAdminAccessToken(anyString())).willReturn("token");

        // when
        AdminLoginResponseDto result = adminAuthService.login(request);

        // then
        assertThat(result.adminAccessToken()).isEqualTo("token");
        assertThat(result.adminId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("관리자 로그인 - 실패 (관리자 없음)")
    void login_Fail_AdminNotFound() {
        // given
        AdminLoginRequestDto request = new AdminLoginRequestDto();
        ReflectionTestUtils.setField(request, "email", "notfound@test.com");

        given(adminRepository.findByEmail(anyString())).willReturn(Optional.empty());

        // when & then
        org.junit.jupiter.api.Assertions.assertThrows(com.example.team3final.common.exception.AdminException.class, () -> {
            adminAuthService.login(request);
        });
    }

    @Test
    @DisplayName("관리자 로그인 - 실패 (비밀번호 불일치)")
    void login_Fail_InvalidPassword() {
        // given
        AdminLoginRequestDto request = new AdminLoginRequestDto();
        ReflectionTestUtils.setField(request, "email", "admin@test.com");
        ReflectionTestUtils.setField(request, "password", "wrong_password");

        Admin admin = Admin.builder()
                .email("admin@test.com")
                .password("encoded_password")
                .build();

        given(adminRepository.findByEmail(anyString())).willReturn(Optional.of(admin));
        given(passwordEncoder.matches(anyString(), anyString())).willReturn(false);

        // when & then
        org.junit.jupiter.api.Assertions.assertThrows(com.example.team3final.common.exception.AdminException.class, () -> {
            adminAuthService.login(request);
        });
    }

    @Test
    @DisplayName("관리자 로그인 - 실패 (계정 비활성화)")
    void login_Fail_AccountInactive() {
        // given
        AdminLoginRequestDto request = new AdminLoginRequestDto();
        ReflectionTestUtils.setField(request, "email", "admin@test.com");
        ReflectionTestUtils.setField(request, "password", "password");
        Admin admin = Admin.builder()
                .email("admin@test.com")
                .password("password")
                .name("admin")
                .role(AdminRole.SUPER_ADMIN)
                .build();
        admin.deactivate();

        given(adminRepository.findByEmail(anyString())).willReturn(Optional.of(admin));
        given(passwordEncoder.matches(anyString(), anyString())).willReturn(true);

        // when & then
        org.junit.jupiter.api.Assertions.assertThrows(com.example.team3final.common.exception.AdminException.class, () -> {
            adminAuthService.login(request);
        });
    }
}
