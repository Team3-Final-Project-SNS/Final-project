package com.example.team3final.domain.auth.service;

import com.example.team3final.common.config.AuthProperties;
import com.example.team3final.common.config.OtpProperties;
import com.example.team3final.common.exception.AuthException;
import com.example.team3final.common.jwt.JwtProvider;
import com.example.team3final.domain.auth.dto.request.OtpRequestDto;
import com.example.team3final.domain.auth.dto.response.OtpResponseDto;
import com.example.team3final.domain.university.service.UniversityInternalService;
import com.example.team3final.domain.user.repository.TermAgreementRepository;
import com.example.team3final.domain.user.service.UserCommandService;
import com.example.team3final.domain.user.service.UserInternalService;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("인증 서비스 단위 테스트")
class AuthServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private OtpService otpService;

    @Mock
    private UniversityInternalService universityInternalService;

    @Mock
    private UserInternalService userInternalService;

    @Mock
    private UserCommandService userCommandService;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private TermAgreementRepository termAgreementRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private OtpProperties otpProperties;

    private AuthProperties authProperties;

    @InjectMocks
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        otpProperties = new OtpProperties();
        authProperties = new AuthProperties();
        authService = new AuthServiceImpl(
                redisTemplate,
                otpService,
                universityInternalService,
                userInternalService,
                userCommandService,
                otpProperties,
                jwtProvider,
                authenticationManager,
                termAgreementRepository,
                passwordEncoder,
                authProperties);
    }

    @Test
    @DisplayName("이메일 OTP 발송은 대학교 도메인과 중복 이메일을 검증하고 Redis 저장 후 메일을 발송한다")
    void sendEmailOtp_shouldStoreOtpAndSendMail() {
        OtpRequestDto request = OtpRequestDto.builder().email("user@test.ac.kr").build();
        when(universityInternalService.isRegisteredActiveUniversity("test.ac.kr")).thenReturn(true);
        when(userInternalService.isEmailAlreadyRegistered("user@test.ac.kr")).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("otp:resend:count:user@test.ac.kr")).thenReturn(null);

        OtpResponseDto result = authService.sendEmailOtp(request);

        assertThat(result.expireSeconds()).isEqualTo(300);
        verify(redisTemplate).delete("otp:attempts:user@test.ac.kr");
        verify(valueOperations).set(eq("otp:code:user@test.ac.kr"), anyString(), eq(Duration.ofSeconds(300)));
        verify(valueOperations).set("otp:resend:count:user@test.ac.kr", "1", Duration.ofSeconds(3600));
        verify(otpService).sendOtp(eq("user@test.ac.kr"), anyString());
    }

    @Test
    @DisplayName("이메일 OTP 발송은 등록되지 않은 대학교 도메인이면 인증 예외를 던진다")
    void sendEmailOtp_shouldThrowWhenUniversityDomainNotRegistered() {
        OtpRequestDto request = OtpRequestDto.builder().email("user@unknown.ac.kr").build();
        when(universityInternalService.isRegisteredActiveUniversity("unknown.ac.kr")).thenReturn(false);

        assertThatThrownBy(() -> authService.sendEmailOtp(request))
                .isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("이메일 OTP 발송은 이미 가입된 이메일이면 인증 예외를 던진다")
    void sendEmailOtp_shouldThrowWhenEmailAlreadyRegistered() {
        OtpRequestDto request = OtpRequestDto.builder().email("user@test.ac.kr").build();
        when(universityInternalService.isRegisteredActiveUniversity("test.ac.kr")).thenReturn(true);
        when(userInternalService.isEmailAlreadyRegistered("user@test.ac.kr")).thenReturn(true);

        assertThatThrownBy(() -> authService.sendEmailOtp(request))
                .isInstanceOf(AuthException.class);
    }
}
