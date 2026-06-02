package com.example.team3final.domain.auth.service;

import com.example.team3final.common.config.OtpProperties;
import com.example.team3final.common.exception.AuthException;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.jwt.JwtProvider;
import com.example.team3final.domain.auth.dto.request.LoginRequestDto;
import com.example.team3final.domain.auth.dto.request.OtpRequestDto;
import com.example.team3final.domain.auth.dto.response.LoginResponseDto;
import com.example.team3final.domain.auth.dto.response.OtpResponseDto;
import com.example.team3final.domain.university.service.UniversityService;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.repository.TermAgreementRepository;
import com.example.team3final.domain.user.service.UserService;
import com.example.team3final.domain.auth.dto.request.OtpVerifyRequestDto;
import com.example.team3final.domain.auth.dto.response.OtpVerifyResponseDto;
import com.example.team3final.domain.university.dto.response.UniversityResponseDto;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

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

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    @DisplayName("OTP 발송 - 성공")
    void sendEmailOtp_Success() {
        // given
        OtpRequestDto request = new OtpRequestDto("test@univ.ac.kr");
        given(universityService.isRegisteredActiveUniversity("univ.ac.kr")).willReturn(true);
        given(userService.isEmailAlreadyRegistered("test@univ.ac.kr")).willReturn(false);
        given(redisTemplate.hasKey(anyString())).willReturn(false);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null);
        given(otpProperties.getMaxResendCount()).willReturn(3);
        given(otpProperties.getExpireSeconds()).willReturn(300L);

        // when
        OtpResponseDto result = authService.sendEmailOtp(request);

        // then
        assertNotNull(result);
        assertEquals(300L, result.expireSeconds());
        verify(otpService).sendOtp(eq("test@univ.ac.kr"), anyString());
    }

    @Test
    @DisplayName("OTP 발송 - 실패 (등록되지 않은 학교 도메인)")
    void sendEmailOtp_Fail_UnregisteredUniversity() {
        // given
        OtpRequestDto request = new OtpRequestDto("test@unknown.ac.kr");
        given(universityService.isRegisteredActiveUniversity("unknown.ac.kr")).willReturn(false);

        // when & then
        AuthException exception = assertThrows(AuthException.class, () -> authService.sendEmailOtp(request));
        assertEquals(ErrorCode.AUTH_UNREGISTERED_UNIVERSITY, exception.getErrorCode());
    }

    @Test
    @DisplayName("로그인 - 성공")
    void login_Success() {
        // given
        LoginRequestDto request = new LoginRequestDto("test@univ.ac.kr", "password");
        HttpServletResponse response = mock(HttpServletResponse.class);
        User user = mock(User.class);
        given(user.getId()).willReturn(1L);
        given(user.getEmail()).willReturn("test@univ.ac.kr");
        given(user.getNickname()).willReturn("Nick");
        given(userService.findByEmail("test@univ.ac.kr")).willReturn(user);
        given(jwtProvider.generateAccessToken("test@univ.ac.kr")).willReturn("accessToken");
        given(jwtProvider.generateRefreshToken("test@univ.ac.kr")).willReturn("refreshToken");
        given(redisTemplate.opsForValue()).willReturn(mock(ValueOperations.class));

        // when
        LoginResponseDto result = authService.login(request, response);

        // then
        assertNotNull(result);
        assertEquals("Nick", result.nickname());
        assertEquals("accessToken", result.accessToken());
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    @DisplayName("로그인 - 실패 (인증 실패)")
    void login_Fail_Authentication() {
        // given
        LoginRequestDto request = new LoginRequestDto("test@univ.ac.kr", "wrong");
        given(authenticationManager.authenticate(any())).willThrow(new BadCredentialsException("fail"));

        // when & then
        AuthException exception = assertThrows(AuthException.class, () -> authService.login(request, mock(HttpServletResponse.class)));
        assertEquals(ErrorCode.AUTH_LOGIN_FAIL, exception.getErrorCode());
    }

    @Test
    @DisplayName("OTP 검증 - 성공")
    void verifyEmailOtp_Success() {
        // given
        OtpVerifyRequestDto request = new OtpVerifyRequestDto("test@univ.ac.kr", "123456");
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        
        // attemptsKey 조회시 0(시도 횟수 0) 반환
        given(valueOperations.get("otp:attempts:test@univ.ac.kr")).willReturn("0");
        // otpCodeKey 조회시 123456 반환
        given(valueOperations.get("otp:code:test@univ.ac.kr")).willReturn("123456");
        
        given(universityService.getUniversityByDomain("univ.ac.kr")).willReturn(new UniversityResponseDto(1L, "Test Univ", "univ.ac.kr"));
        given(jwtProvider.generateSignupToken(anyString())).willReturn("signupToken");

        // when
        OtpVerifyResponseDto result = authService.verifyEmailOtp(request, mock(HttpServletResponse.class));

        // then
        assertNotNull(result);
        assertEquals(1L, result.universityId());
        verify(redisTemplate).delete("otp:code:test@univ.ac.kr");
    }

    @Test
    @DisplayName("OTP 검증 - 실패 (코드 불일치)")
    void verifyEmailOtp_Fail_Mismatch() {
        // given
        OtpVerifyRequestDto request = new OtpVerifyRequestDto("test@univ.ac.kr", "123456");
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        
        // attemptsKey 조회시 0 반환
        given(valueOperations.get("otp:attempts:test@univ.ac.kr")).willReturn("0");
        // otpCodeKey 조회시 다른 코드 반환
        given(valueOperations.get("otp:code:test@univ.ac.kr")).willReturn("999999");

        // when & then
        AuthException exception = assertThrows(AuthException.class, () -> authService.verifyEmailOtp(request, mock(HttpServletResponse.class)));
        assertEquals(ErrorCode.OTP_CODE_MISMATCH, exception.getErrorCode());
        verify(valueOperations).increment("otp:attempts:test@univ.ac.kr");
    }

    @Test
    @DisplayName("로그아웃 - 성공")
    void logout_Success() {
        // given
        String refreshToken = "validToken";
        given(jwtProvider.validateToken(refreshToken)).willReturn(true);
        given(jwtProvider.getEmailFromToken(refreshToken)).willReturn("test@univ.ac.kr");

        // when
        authService.logout(refreshToken, mock(HttpServletResponse.class));

        // then
        verify(redisTemplate).delete(anyString());
    }
}
