package com.example.team3final.domain.admin.auth.service;

import com.example.team3final.common.exception.AdminException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("관리자 인증 서비스 단위 테스트")
class AdminAuthServiceTest {

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @InjectMocks
    private AdminAuthServiceImpl adminAuthService;

    @Test
    @DisplayName("관리자 로그인은 이메일과 비밀번호를 검증하고 관리자 액세스 토큰을 반환한다")
    void login_shouldReturnAdminAccessToken() {
        Admin admin = Admin.createAdmin("admin@test.com", "encoded-password", "관리자", AdminRole.SUPER_ADMIN);
        ReflectionTestUtils.setField(admin, "id", 1L);
        AdminLoginRequestDto request = loginRequest("admin@test.com", "password");
        when(adminRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("password", "encoded-password")).thenReturn(true);
        when(jwtProvider.generateAdminAccessToken("admin@test.com")).thenReturn("admin-access-token");

        AdminLoginResponseDto result = adminAuthService.login(request);

        assertThat(result.adminId()).isEqualTo(1L);
        assertThat(result.adminAccessToken()).isEqualTo("admin-access-token");
        verify(adminRepository).findByEmail("admin@test.com");
        verify(passwordEncoder).matches("password", "encoded-password");
        verify(jwtProvider).generateAdminAccessToken("admin@test.com");
    }

    @Test
    @DisplayName("관리자 로그인을 존재하지 않는 이메일로 요청하면 관리자 예외를 던진다")
    void login_shouldThrowWhenAdminNotFound() {
        AdminLoginRequestDto request = loginRequest("missing@test.com", "password");
        when(adminRepository.findByEmail("missing@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminAuthService.login(request))
                .isInstanceOf(AdminException.class);

        verify(passwordEncoder, never()).matches(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        verify(jwtProvider, never()).generateAdminAccessToken(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("관리자 로그인을 잘못된 비밀번호로 요청하면 관리자 예외를 던진다")
    void login_shouldThrowWhenPasswordMismatch() {
        Admin admin = Admin.createAdmin("admin@test.com", "encoded-password", "관리자", AdminRole.SUPER_ADMIN);
        AdminLoginRequestDto request = loginRequest("admin@test.com", "wrong-password");
        when(adminRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> adminAuthService.login(request))
                .isInstanceOf(AdminException.class);

        verify(jwtProvider, never()).generateAdminAccessToken(org.mockito.ArgumentMatchers.anyString());
    }

    private AdminLoginRequestDto loginRequest(String email, String password) {
        AdminLoginRequestDto request = new AdminLoginRequestDto();
        ReflectionTestUtils.setField(request, "email", email);
        ReflectionTestUtils.setField(request, "password", password);
        return request;
    }
}
