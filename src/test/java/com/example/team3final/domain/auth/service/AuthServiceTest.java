package com.example.team3final.domain.auth.service;

import com.example.team3final.common.config.AuthProperties;
import com.example.team3final.common.config.OtpProperties;
import com.example.team3final.common.jwt.JwtProvider;
import com.example.team3final.domain.auth.dto.request.LoginRequestDto;
import com.example.team3final.domain.auth.dto.request.OtpRequestDto;
import com.example.team3final.domain.auth.dto.request.OtpVerifyRequestDto;
import com.example.team3final.domain.auth.dto.request.SignupRequestDto;
import com.example.team3final.domain.auth.dto.response.LoginResponseDto;
import com.example.team3final.domain.auth.dto.response.OtpResponseDto;
import com.example.team3final.domain.auth.dto.response.OtpVerifyResponseDto;
import com.example.team3final.domain.auth.dto.response.SignupResponseDto;
import com.example.team3final.domain.auth.dto.response.TokenResponseDto;
import com.example.team3final.domain.university.dto.response.UniversityResponseDto;
import com.example.team3final.domain.university.service.UniversityService;
import com.example.team3final.domain.user.dto.request.WithdrawRequestDto;
import com.example.team3final.domain.user.dto.response.TermAgreementDto;
import com.example.team3final.domain.user.dto.response.WithdrawResponseDto;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.enums.Gender;
import com.example.team3final.domain.user.repository.TermAgreementRepository;
import com.example.team3final.domain.user.service.UserService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthServiceImpl authService;

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private OtpService otpService;
    @Mock
    private UniversityService universityService;
    @Mock
    private UserService userService;
    @Mock
    private OtpProperties otpProperties;
    @Mock
    private JwtProvider jwtProvider;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private TermAgreementRepository termAgreementRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthProperties authProperties;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    @DisplayName("OTP 이메일 발송 - 성공")
    void sendEmailOtp_Success() {
        // given
        String email = "test@univ.ac.kr";
        OtpRequestDto request = new OtpRequestDto(email);

        given(universityService.isRegisteredActiveUniversity(anyString())).willReturn(true);
        given(userService.isEmailAlreadyRegistered(email)).willReturn(false);
        given(redisTemplate.hasKey(anyString())).willReturn(false);
        given(otpProperties.getMaxResendCount()).willReturn(3);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null);
        given(otpProperties.getExpireSeconds()).willReturn(300L);

        // when
        OtpResponseDto result = authService.sendEmailOtp(request);

        // then
        assertThat(result.expireSeconds()).isEqualTo(300L);
        verify(otpService).sendOtp(eq(email), anyString());
    }

    @Test
    @DisplayName("OTP 이메일 검증 - 성공")
    void verifyEmailOtp_Success() {
        OtpVerifyRequestDto request = OtpVerifyRequestDto.builder()
                .email("test@univ.ac.kr")
                .otpCode("123456")
                .build();
        MockHttpServletResponse response = new MockHttpServletResponse();
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null, "123456");
        given(universityService.getUniversityByDomain("univ.ac.kr"))
                .willReturn(UniversityResponseDto.builder()
                        .universityId(1L)
                        .universityName("Test Univ")
                        .eDomain("univ.ac.kr")
                        .build());
        given(jwtProvider.generateSignupToken("test@univ.ac.kr")).willReturn("signup-token");

        OtpVerifyResponseDto result = authService.verifyEmailOtp(request, response);

        assertThat(result.universityId()).isEqualTo(1L);
        assertThat(response.getCookie("signup_token").getValue()).isEqualTo("signup-token");
    }

    @Test
    @DisplayName("회원가입 - 성공")
    void signup_Success() {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setCookies(new Cookie("signup_token", "signup-token"));
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        SignupRequestDto request = SignupRequestDto.builder()
                .password("password123")
                .name("name")
                .nickname("nick")
                .major("major")
                .studentNumber("24")
                .birthDate(LocalDate.of(2000, 1, 1))
                .gender(Gender.MALE)
                .termAgreements(List.of(new TermAgreementDto("service-v1", true)))
                .build();
        User savedUser = User.builder()
                .email("test@univ.ac.kr")
                .password("encoded")
                .name("name")
                .nickname("nick")
                .universityId(1L)
                .major("major")
                .studentNumber("24")
                .birthDate(LocalDate.of(2000, 1, 1))
                .gender(Gender.MALE)
                .build();
        ReflectionTestUtils.setField(savedUser, "id", 10L);
        savedUser.addFreePoint(10000);
        given(jwtProvider.validateToken("signup-token")).willReturn(true);
        given(jwtProvider.getTokenType("signup-token")).willReturn("SIGNUP");
        given(jwtProvider.getEmailFromToken("signup-token")).willReturn("test@univ.ac.kr");
        given(userService.isEmailAlreadyRegistered("test@univ.ac.kr")).willReturn(false);
        given(userService.existsByNickname("nick")).willReturn(false);
        given(authProperties.getRequiredTermVersions()).willReturn(List.of("service-v1"));
        given(universityService.getUniversityByDomain("univ.ac.kr"))
                .willReturn(UniversityResponseDto.builder()
                        .universityId(1L)
                        .universityName("Test Univ")
                        .eDomain("univ.ac.kr")
                        .build());
        given(passwordEncoder.encode("password123")).willReturn("encoded");
        given(userService.createUser(eq("test@univ.ac.kr"), eq("encoded"), eq("name"), eq("nick"),
                eq(1L), eq("major"), eq("24"), eq(LocalDate.of(2000, 1, 1)), eq(Gender.MALE)))
                .willReturn(savedUser);
        given(jwtProvider.generateRefreshToken("test@univ.ac.kr")).willReturn("refresh-token");
        given(jwtProvider.generateAccessToken("test@univ.ac.kr")).willReturn("access-token");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        SignupResponseDto result = authService.signup(request, httpRequest, httpResponse);

        assertThat(result.userId()).isEqualTo(10L);
        assertThat(result.accessToken()).isEqualTo("access-token");
        verify(termAgreementRepository).saveAll(any());
    }

    @Test
    @DisplayName("로그인 - 성공")
    void login_Success() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoginRequestDto request = LoginRequestDto.builder()
                .email("test@univ.ac.kr")
                .password("password")
                .build();
        User user = User.builder()
                .email("test@univ.ac.kr")
                .password("encoded")
                .name("name")
                .nickname("nick")
                .universityId(1L)
                .major("major")
                .studentNumber("24")
                .birthDate(LocalDate.of(2000, 1, 1))
                .gender(Gender.MALE)
                .build();
        ReflectionTestUtils.setField(user, "id", 10L);
        given(userService.findByEmail("test@univ.ac.kr")).willReturn(user);
        given(jwtProvider.generateAccessToken("test@univ.ac.kr")).willReturn("access-token");
        given(jwtProvider.generateRefreshToken("test@univ.ac.kr")).willReturn("refresh-token");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        LoginResponseDto result = authService.login(request, response);

        assertThat(result.userId()).isEqualTo(10L);
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(response.getCookie("refresh_token").getValue()).isEqualTo("refresh-token");
    }

    @Test
    @DisplayName("토큰 재발급 - 성공")
    void refresh_Success() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        given(jwtProvider.validateToken("old-refresh")).willReturn(true);
        given(jwtProvider.getTokenType("old-refresh")).willReturn("REFRESH");
        given(jwtProvider.getEmailFromToken("old-refresh")).willReturn("test@univ.ac.kr");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("refresh:test@univ.ac.kr")).willReturn("old-refresh");
        given(jwtProvider.generateAccessToken("test@univ.ac.kr")).willReturn("new-access");
        given(jwtProvider.generateRefreshToken("test@univ.ac.kr")).willReturn("new-refresh");

        TokenResponseDto result = authService.refresh("old-refresh", response);

        assertThat(result.accessToken()).isEqualTo("new-access");
        assertThat(response.getCookie("refresh_token").getValue()).isEqualTo("new-refresh");
    }

    @Test
    @DisplayName("로그아웃 - 성공")
    void logout_Success() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        given(jwtProvider.validateToken("refresh-token")).willReturn(true);
        given(jwtProvider.getEmailFromToken("refresh-token")).willReturn("test@univ.ac.kr");

        authService.logout("refresh-token", response);

        verify(redisTemplate).delete("refresh:test@univ.ac.kr");
        assertThat(response.getCookie("refresh_token").getMaxAge()).isZero();
    }

    @Test
    @DisplayName("회원 탈퇴 - 성공")
    void withdraw_Success() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        WithdrawRequestDto request = new WithdrawRequestDto();
        ReflectionTestUtils.setField(request, "password", "password");
        given(jwtProvider.validateToken("refresh-token")).willReturn(true);
        given(jwtProvider.getEmailFromToken("refresh-token")).willReturn("test@univ.ac.kr");

        WithdrawResponseDto result = authService.withdraw(10L, request, "refresh-token", response);

        assertThat(result.userId()).isEqualTo(10L);
        verify(userService).withdrawUser(10L, "password");
        verify(redisTemplate).delete("refresh:test@univ.ac.kr");
    }
}
