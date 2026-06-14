package com.example.team3final.domain.auth.service;

import com.example.team3final.common.config.AuthProperties;
import com.example.team3final.common.config.OtpProperties;
import com.example.team3final.common.exception.AuthException;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    // =====================================================
    // 테스트 대상 (실제 구현체)
    // @InjectMocks: @Mock 객체들을 생성자 주입으로 자동 연결
    // =====================================================
    @InjectMocks
    private AuthServiceImpl authService;

    // =====================================================
    // Mock 객체 선언
    // @Mock: 실제 구현 없이 동작을 지정할 수 있는 가짜 객체
    // =====================================================
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private OtpService otpService;
    @Mock private UniversityService universityService;
    @Mock private UserService userService;
    @Mock private OtpProperties otpProperties;
    @Mock private JwtProvider jwtProvider;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private TermAgreementRepository termAgreementRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthProperties authProperties;

    // redisTemplate.opsForValue().set(...) 호출 체인을 처리하기 위한 Mock
    @Mock private ValueOperations<String, String> valueOperations;

    // =====================================================
    // 공통 테스트 픽스처 (테스트 전반에서 재사용)
    // =====================================================
    private static final String TEST_EMAIL    = "test@univ.ac.kr";
    private static final String TEST_DOMAIN   = "univ.ac.kr";
    private static final String SIGNUP_TOKEN  = "signup-token";
    private static final String ACCESS_TOKEN  = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";
    private static final String NEW_ACCESS_TOKEN  = "new-access-token";
    private static final String NEW_REFRESH_TOKEN = "new-refresh-token";

    // 멀티 디바이스 테스트용 고정 deviceId
    // 실제 서비스는 UUID.randomUUID()이지만 테스트에서는 예측 가능한 값 사용
    private static final String DEVICE_ID = "test-device-uuid-1234";

    // =====================================================
    // 공통 헬퍼: User 객체 생성
    // 여러 테스트에서 동일한 User가 필요하므로 메서드로 추출
    // =====================================================
    private User buildTestUser() {
        User user = User.builder()
                .email(TEST_EMAIL)
                .password("encoded")
                .name("name")
                .nickname("nick")
                .universityId(1L)
                .major("major")
                .studentNumber("24")
                .birthDate(LocalDate.of(2000, 1, 1))
                .gender(Gender.MALE)
                .build();
        // id는 DB 자동생성 필드라 ReflectionTestUtils로 강제 주입
        ReflectionTestUtils.setField(user, "id", 10L);
        return user;
    }

    // =====================================================
    // OTP 발송 테스트
    // =====================================================

    @Test
    @DisplayName("OTP 이메일 발송 - 성공")
    void sendEmailOtp_Success() {
        // given
        OtpRequestDto request = new OtpRequestDto(TEST_EMAIL);

        given(universityService.isRegisteredActiveUniversity(TEST_DOMAIN)).willReturn(true);
        given(userService.isEmailAlreadyRegistered(TEST_EMAIL)).willReturn(false);
        given(otpProperties.getMaxResendCount()).willReturn(3);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        // 첫 발송이므로 발송 횟수 키 없음 → null
        given(valueOperations.get(anyString())).willReturn(null);
        given(otpProperties.getExpireSeconds()).willReturn(300L);

        // when
        OtpResponseDto result = authService.sendEmailOtp(request);

        // then
        assertThat(result.expireSeconds()).isEqualTo(300L);
        // OTP 이메일 발송 실제 호출 여부 확인 (OTP 코드는 랜덤이므로 anyString())
        verify(otpService).sendOtp(eq(TEST_EMAIL), anyString());
    }

    @Test
    @DisplayName("OTP 이메일 발송 - 미등록 학교 도메인이면 예외")
    void sendEmailOtp_UnregisteredUniversity() {
        // given
        OtpRequestDto request = new OtpRequestDto(TEST_EMAIL);
        given(universityService.isRegisteredActiveUniversity(TEST_DOMAIN)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.sendEmailOtp(request))
                .isInstanceOf(AuthException.class);

        // OTP 발송은 절대 호출되면 안 됨
        verify(otpService, never()).sendOtp(anyString(), anyString());
    }

    @Test
    @DisplayName("OTP 이메일 발송 - 이미 가입된 이메일이면 예외")
    void sendEmailOtp_AlreadyRegistered() {
        // given
        OtpRequestDto request = new OtpRequestDto(TEST_EMAIL);
        given(universityService.isRegisteredActiveUniversity(TEST_DOMAIN)).willReturn(true);
        given(userService.isEmailAlreadyRegistered(TEST_EMAIL)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> authService.sendEmailOtp(request))
                .isInstanceOf(AuthException.class);
    }

    // =====================================================
    // OTP 검증 테스트
    // =====================================================

    @Test
    @DisplayName("OTP 이메일 검증 - 성공")
    void verifyEmailOtp_Success() {
        // given
        OtpVerifyRequestDto request = OtpVerifyRequestDto.builder()
                .email(TEST_EMAIL)
                .otpCode("123456")
                .build();
        MockHttpServletResponse response = new MockHttpServletResponse();

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        // 첫 번째 get(): 시도 횟수 키 → null (첫 시도)
        // 두 번째 get(): OTP 키 → "123456" (저장된 OTP)
        given(valueOperations.get(anyString())).willReturn(null, "123456");

        given(universityService.getUniversityByDomain(TEST_DOMAIN))
                .willReturn(UniversityResponseDto.builder()
                        .universityId(1L)
                        .universityName("Test Univ")
                        .eDomain(TEST_DOMAIN)
                        .build());
        given(jwtProvider.generateSignupToken(TEST_EMAIL)).willReturn(SIGNUP_TOKEN);

        // when
        OtpVerifyResponseDto result = authService.verifyEmailOtp(request, response);

        // then
        assertThat(result.universityId()).isEqualTo(1L);
        // signup_token이 HttpOnly 쿠키로 내려갔는지 확인
        Cookie signupCookie = response.getCookie("signup_token");
        assertThat(signupCookie).isNotNull();
        assertThat(signupCookie).satisfies(c -> assertThat(c.getValue()).isEqualTo(SIGNUP_TOKEN));
    }

    // =====================================================
    // 회원가입 테스트
    // =====================================================

    @Test
    @DisplayName("회원가입 - 성공 + device_id 쿠키 발급 확인")
    void signup_Success() {
        // given
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        // OTP 검증 시 받은 signup_token 쿠키를 요청에 포함
        httpRequest.setCookies(new Cookie("signup_token", SIGNUP_TOKEN));
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

        User savedUser = buildTestUser();
        savedUser.addFreePoint(10000); // 가입 보너스 포인트 지급

        // signup_token 검증
        given(jwtProvider.validateToken(SIGNUP_TOKEN)).willReturn(true);
        given(jwtProvider.getTokenType(SIGNUP_TOKEN)).willReturn("SIGNUP");
        given(jwtProvider.getEmailFromToken(SIGNUP_TOKEN)).willReturn(TEST_EMAIL);

        // 중복 검사
        given(userService.isEmailAlreadyRegistered(TEST_EMAIL)).willReturn(false);
        given(userService.existsByNickname("nick")).willReturn(false);

        // 필수 약관 목록
        given(authProperties.getRequiredTermVersions()).willReturn(List.of("service-v1"));

        // 학교 정보 조회
        given(universityService.getUniversityByDomain(TEST_DOMAIN))
                .willReturn(UniversityResponseDto.builder()
                        .universityId(1L)
                        .universityName("Test Univ")
                        .eDomain(TEST_DOMAIN)
                        .build());

        // 비밀번호 암호화
        given(passwordEncoder.encode("password123")).willReturn("encoded");

        // 유저 생성
        given(userService.createUser(eq(TEST_EMAIL), eq("encoded"), eq("name"), eq("nick"),
                eq(1L), eq("major"), eq("24"), eq(LocalDate.of(2000, 1, 1)), eq(Gender.MALE)))
                .willReturn(savedUser);

        // [멀티 디바이스] Refresh Token 발급: email + deviceId(UUID → anyString())
        given(jwtProvider.generateRefreshToken(eq(TEST_EMAIL), anyString())).willReturn(REFRESH_TOKEN);
        given(jwtProvider.generateAccessToken(TEST_EMAIL)).willReturn(ACCESS_TOKEN);
        given(jwtProvider.getRefreshTokenValidityTime()).willReturn(1209600000L);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        // when
        SignupResponseDto result = authService.signup(request, httpRequest, httpResponse);

        // then - 응답 DTO 검증
        assertThat(result.userId()).isEqualTo(10L);
        assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(result.point()).isEqualTo(10000); // SignupResponseDto 필드명: point

        // 약관 저장 호출 확인
        verify(termAgreementRepository).saveAll(any());

        // refresh_token 쿠키 검증
        Cookie refreshCookie = httpResponse.getCookie("refresh_token");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie).satisfies(c -> {
            assertThat(c.getValue()).isEqualTo(REFRESH_TOKEN);
            assertThat(c.isHttpOnly()).isTrue();
        });

        // [핵심] device_id 쿠키 발급 확인 (멀티 디바이스 핵심 검증)
        // device_id는 UUID.randomUUID()로 생성되므로 값이 비어있지 않은지만 확인
        Cookie deviceCookie = httpResponse.getCookie("device_id");
        assertThat(deviceCookie).isNotNull();
        assertThat(deviceCookie).satisfies(c -> {
            assertThat(c.getValue()).isNotBlank();
            assertThat(c.isHttpOnly()).isTrue();
        });
    }

    @Test
    @DisplayName("회원가입 - signup_token 쿠키 없으면 예외")
    void signup_NoSignupToken() {
        // given: 쿠키가 없는 요청
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        SignupRequestDto request = SignupRequestDto.builder()
                .password("pw").name("name").nickname("nick").major("major")
                .studentNumber("24").birthDate(LocalDate.of(2000, 1, 1))
                .gender(Gender.MALE).termAgreements(List.of())
                .build();

        // when & then
        assertThatThrownBy(() -> authService.signup(request, httpRequest, httpResponse))
                .isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("회원가입 - 닉네임 중복이면 예외")
    void signup_DuplicateNickname() {
        // given
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setCookies(new Cookie("signup_token", SIGNUP_TOKEN));
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        SignupRequestDto request = SignupRequestDto.builder()
                .password("pw").name("name").nickname("nick").major("major")
                .studentNumber("24").birthDate(LocalDate.of(2000, 1, 1))
                .gender(Gender.MALE).termAgreements(List.of())
                .build();

        given(jwtProvider.validateToken(SIGNUP_TOKEN)).willReturn(true);
        given(jwtProvider.getTokenType(SIGNUP_TOKEN)).willReturn("SIGNUP");
        given(jwtProvider.getEmailFromToken(SIGNUP_TOKEN)).willReturn(TEST_EMAIL);
        given(userService.isEmailAlreadyRegistered(TEST_EMAIL)).willReturn(false);
        // 닉네임 중복
        given(userService.existsByNickname("nick")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> authService.signup(request, httpRequest, httpResponse))
                .isInstanceOf(AuthException.class);
    }

    // =====================================================
    // 로그인 테스트
    // =====================================================

    @Test
    @DisplayName("로그인 - 성공 + device_id 쿠키 발급 확인")
    void login_Success() {
        // given
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoginRequestDto request = LoginRequestDto.builder()
                .email(TEST_EMAIL)
                .password("password")
                .build();

        User user = buildTestUser();

        // authenticationManager.authenticate(): 예외 없이 통과 = 인증 성공
        given(userService.findByEmail(TEST_EMAIL)).willReturn(user);
        given(jwtProvider.generateAccessToken(TEST_EMAIL)).willReturn(ACCESS_TOKEN);

        // [멀티 디바이스] deviceId 포함 Refresh Token 발급
        given(jwtProvider.generateRefreshToken(eq(TEST_EMAIL), anyString())).willReturn(REFRESH_TOKEN);
        given(jwtProvider.getRefreshTokenValidityTime()).willReturn(1209600000L);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        // when
        LoginResponseDto result = authService.login(request, response);

        // then - 응답 DTO 검증
        assertThat(result.userId()).isEqualTo(10L);
        assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);

        // refresh_token HttpOnly 쿠키 검증
        Cookie refreshCookie = response.getCookie("refresh_token");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie).satisfies(c -> {
            assertThat(c.getValue()).isEqualTo(REFRESH_TOKEN);
            assertThat(c.isHttpOnly()).isTrue();
        });

        // [핵심] device_id 쿠키 발급 확인
        Cookie deviceCookie = response.getCookie("device_id");
        assertThat(deviceCookie).isNotNull();
        assertThat(deviceCookie).satisfies(c -> {
            assertThat(c.getValue()).isNotBlank(); // UUID이므로 값 존재 여부만 확인
            assertThat(c.isHttpOnly()).isTrue();
        });
    }

    @Test
    @DisplayName("로그인 - 잘못된 비밀번호면 예외")
    void login_BadCredentials() {
        // given
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoginRequestDto request = LoginRequestDto.builder()
                .email(TEST_EMAIL)
                .password("wrong-password")
                .build();

        // Spring Security: 이메일/비밀번호 불일치 시 BadCredentialsException
        given(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .willThrow(new BadCredentialsException("bad credentials"));

        // when & then
        assertThatThrownBy(() -> authService.login(request, response))
                .isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("로그인 - 탈퇴 계정이면 예외")
    void login_WithdrawnAccount() {
        // given
        MockHttpServletResponse response = new MockHttpServletResponse();
        LoginRequestDto request = LoginRequestDto.builder()
                .email(TEST_EMAIL)
                .password("password")
                .build();

        // Spring Security: 계정 비활성화(탈퇴) 시 DisabledException
        given(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .willThrow(new DisabledException("disabled"));

        // when & then
        assertThatThrownBy(() -> authService.login(request, response))
                .isInstanceOf(AuthException.class);
    }

    // =====================================================
    // 토큰 재발급 테스트
    // =====================================================

    @Test
    @DisplayName("토큰 재발급 - 성공")
    void refresh_Success() {
        // given
        MockHttpServletResponse response = new MockHttpServletResponse();

        // [멀티 디바이스] Redis key: "refresh:{email}:{deviceId}"
        String redisKey = "refresh:" + TEST_EMAIL + ":" + DEVICE_ID;

        given(jwtProvider.validateToken(REFRESH_TOKEN)).willReturn(true);
        given(jwtProvider.getTokenType(REFRESH_TOKEN)).willReturn("REFRESH");
        given(jwtProvider.getEmailFromToken(REFRESH_TOKEN)).willReturn(TEST_EMAIL);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        // 해당 디바이스의 저장된 토큰 반환 (요청 토큰과 일치)
        given(valueOperations.get(redisKey)).willReturn(REFRESH_TOKEN);

        // 새 토큰 발급: 동일한 deviceId 유지 (RTR)
        given(jwtProvider.generateAccessToken(TEST_EMAIL)).willReturn(NEW_ACCESS_TOKEN);
        given(jwtProvider.generateRefreshToken(eq(TEST_EMAIL), eq(DEVICE_ID))).willReturn(NEW_REFRESH_TOKEN);
        given(jwtProvider.getRefreshTokenValidityTime()).willReturn(1209600000L);

        // when
        TokenResponseDto result = authService.refresh(REFRESH_TOKEN, DEVICE_ID, response);

        // then
        assertThat(result.accessToken()).isEqualTo(NEW_ACCESS_TOKEN);

        // 새 refresh_token 쿠키 갱신 확인 (RTR: Refresh Token Rotation)
        Cookie refreshCookie = response.getCookie("refresh_token");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie).satisfies(c -> assertThat(c.getValue()).isEqualTo(NEW_REFRESH_TOKEN));

        // Redis에 새 토큰으로 업데이트됐는지 확인
        verify(valueOperations).set(eq(redisKey), eq(NEW_REFRESH_TOKEN), any());
    }

    @Test
    @DisplayName("토큰 재발급 - refreshToken이 null이면 예외")
    void refresh_NullRefreshToken() {
        // given
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when & then
        assertThatThrownBy(() -> authService.refresh(null, DEVICE_ID, response))
                .isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("토큰 재발급 - deviceId가 null이면 예외")
    void refresh_NullDeviceId() {
        // given
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when & then
        assertThatThrownBy(() -> authService.refresh(REFRESH_TOKEN, null, response))
                .isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("토큰 재발급 - Redis 저장 토큰과 다르면 예외 (탈취 감지)")
    void refresh_TokenMismatch() {
        // given
        MockHttpServletResponse response = new MockHttpServletResponse();
        String redisKey = "refresh:" + TEST_EMAIL + ":" + DEVICE_ID;

        given(jwtProvider.validateToken(REFRESH_TOKEN)).willReturn(true);
        given(jwtProvider.getTokenType(REFRESH_TOKEN)).willReturn("REFRESH");
        given(jwtProvider.getEmailFromToken(REFRESH_TOKEN)).willReturn(TEST_EMAIL);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        // Redis에 다른 토큰이 저장됨 → 이미 사용된 토큰 또는 탈취된 토큰
        given(valueOperations.get(redisKey)).willReturn("already-rotated-token");

        // when & then
        assertThatThrownBy(() -> authService.refresh(REFRESH_TOKEN, DEVICE_ID, response))
                .isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("토큰 재발급 - 다른 디바이스의 deviceId로 시도하면 예외 (디바이스 세션 격리 검증)")
    void refresh_WrongDeviceId() {
        // given: device1의 토큰으로 device2의 deviceId를 사용해 재발급 시도
        MockHttpServletResponse response = new MockHttpServletResponse();
        String wrongDeviceId = "wrong-device-uuid-9999";
        // 잘못된 deviceId로 조합된 key → Redis에 해당 세션 없음
        String wrongRedisKey = "refresh:" + TEST_EMAIL + ":" + wrongDeviceId;

        given(jwtProvider.validateToken(REFRESH_TOKEN)).willReturn(true);
        given(jwtProvider.getTokenType(REFRESH_TOKEN)).willReturn("REFRESH");
        given(jwtProvider.getEmailFromToken(REFRESH_TOKEN)).willReturn(TEST_EMAIL);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        // 잘못된 key → null 반환 (해당 디바이스 세션 없음)
        given(valueOperations.get(wrongRedisKey)).willReturn(null);

        // when & then: 다른 디바이스 세션에 접근 불가
        assertThatThrownBy(() -> authService.refresh(REFRESH_TOKEN, wrongDeviceId, response))
                .isInstanceOf(AuthException.class);
    }

    // =====================================================
    // 로그아웃 테스트
    // =====================================================

    @Test
    @DisplayName("로그아웃 - 성공 + 해당 디바이스 세션만 삭제 확인")
    void logout_Success() {
        // given
        MockHttpServletResponse response = new MockHttpServletResponse();

        // [멀티 디바이스] 삭제되어야 할 Redis key
        String expectedRedisKey = "refresh:" + TEST_EMAIL + ":" + DEVICE_ID;

        given(jwtProvider.validateToken(REFRESH_TOKEN)).willReturn(true);
        given(jwtProvider.getEmailFromToken(REFRESH_TOKEN)).willReturn(TEST_EMAIL);

        // when
        authService.logout(REFRESH_TOKEN, DEVICE_ID, response);

        // then
        // [핵심] 해당 디바이스의 key만 삭제 (다른 디바이스 세션은 유지)
        verify(redisTemplate).delete(expectedRedisKey);

        // refresh_token 쿠키 만료 확인 (Max-Age=0 → 브라우저 즉시 삭제)
        Cookie refreshCookie = response.getCookie("refresh_token");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie).satisfies(c -> assertThat(c.getMaxAge()).isZero());

        // device_id 쿠키도 만료됐는지 확인
        Cookie deviceCookie = response.getCookie("device_id");
        assertThat(deviceCookie).isNotNull();
        assertThat(deviceCookie).satisfies(c -> assertThat(c.getMaxAge()).isZero());
    }

    @Test
    @DisplayName("로그아웃 - refreshToken이 null이어도 쿠키는 만료 처리됨")
    void logout_NullToken_StillExpiresCookies() {
        // given
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when: 토큰 없이 로그아웃 (브라우저 쿠키만 있는 상태)
        authService.logout(null, DEVICE_ID, response);

        // then: Redis 삭제는 호출되지 않음 (토큰이 없으므로 이메일 추출 불가)
        verify(redisTemplate, never()).delete(anyString());

        // 쿠키는 만료 처리됨
        Cookie refreshCookie = response.getCookie("refresh_token");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie).satisfies(c -> assertThat(c.getMaxAge()).isZero());

        Cookie deviceCookie = response.getCookie("device_id");
        assertThat(deviceCookie).isNotNull();
        assertThat(deviceCookie).satisfies(c -> assertThat(c.getMaxAge()).isZero());
    }

    // =====================================================
    // 회원 탈퇴 테스트
    // =====================================================

    @Test
    @DisplayName("회원 탈퇴 - 성공")
    void withdraw_Success() {
        // given
        MockHttpServletResponse response = new MockHttpServletResponse();
        WithdrawRequestDto request = new WithdrawRequestDto();
        ReflectionTestUtils.setField(request, "password", "password");

        given(jwtProvider.validateToken(REFRESH_TOKEN)).willReturn(true);
        given(jwtProvider.getEmailFromToken(REFRESH_TOKEN)).willReturn(TEST_EMAIL);

        // when
        WithdrawResponseDto result = authService.withdraw(10L, request, REFRESH_TOKEN, response);

        // then
        assertThat(result.userId()).isEqualTo(10L);
        // 비밀번호 검증 + 계정 비활성화 호출 확인
        verify(userService).withdrawUser(10L, "password");
        // refresh_token 쿠키 만료 확인
        Cookie refreshCookie = response.getCookie("refresh_token");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie).satisfies(c -> assertThat(c.getMaxAge()).isZero());
    }
}