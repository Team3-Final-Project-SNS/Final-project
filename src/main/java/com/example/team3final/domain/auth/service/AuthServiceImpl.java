package com.example.team3final.domain.auth.service;

import com.example.team3final.common.config.AuthProperties;
import com.example.team3final.common.config.OtpProperties;
import com.example.team3final.common.exception.AuthException;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.jwt.JwtProvider;
import com.example.team3final.domain.auth.dto.request.LoginRequestDto;
import com.example.team3final.domain.auth.dto.request.OtpRequestDto;
import com.example.team3final.domain.auth.dto.request.OtpVerifyRequestDto;
import com.example.team3final.domain.auth.dto.request.SignupRequestDto;
import com.example.team3final.domain.auth.dto.response.*;
import com.example.team3final.domain.auth.util.OtpGenerator;
import com.example.team3final.domain.auth.util.OtpRedisKeyUtil;
import com.example.team3final.domain.university.dto.response.UniversityResponseDto;
import com.example.team3final.domain.university.service.UniversityInternalService;
import com.example.team3final.domain.user.dto.request.WithdrawRequestDto;
import com.example.team3final.domain.user.dto.response.WithdrawResponseDto;
import com.example.team3final.domain.user.entity.TermAgreement;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.repository.TermAgreementRepository;
import com.example.team3final.domain.user.service.UserCommandService;
import com.example.team3final.domain.user.service.UserInternalService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService{

    private final StringRedisTemplate redisTemplate;
    private final OtpService otpService;
    private final UniversityInternalService universityInternalService;
    private final UserInternalService userInternalService;
    private final UserCommandService userCommandService;
    private final OtpProperties otpProperties;
    private final JwtProvider jwtProvider;
    private final AuthenticationManager authenticationManager;
    private final TermAgreementRepository termAgreementRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String REFRESH_TOKEN_KEY_PREFIX = "refresh:";
    private static final String DEVICE_ID_COOKIE_NAME = "device_id";

    private final AuthProperties authProperties; // 회원정보시 약관 동의

    // ======== OTP 발송 ======================
    @Override
    public OtpResponseDto sendEmailOtp(OtpRequestDto request) {
        String email = request.getEmail();


        // 1. 등록된 학교 도메인인지 검증 (Service to Service)
        String emailDomain = email.substring(email.indexOf("@") + 1);
        boolean isRegisteredUniversity = universityInternalService.isRegisteredActiveUniversity(emailDomain);
        if (!isRegisteredUniversity) {
            throw new AuthException(ErrorCode.AUTH_UNREGISTERED_UNIVERSITY);
        }

        // 2. 이미 가입된 이메일인지 검증 (Service to Service)
        boolean isAlreadyRegistered = userInternalService.isEmailAlreadyRegistered(email);
        if (isAlreadyRegistered) {
            throw new AuthException(ErrorCode.AUTH_ALREADY_REGISTERED_EMAIL);
        }

        // 3. 일일 최대 재발송 횟수 체크 (1시간 내 5회)
        String resendCountKey = OtpRedisKeyUtil.resendCountKey(email);
        String countStr = redisTemplate.opsForValue().get(resendCountKey);
        int currentCount = (countStr == null) ? 0 : Integer.parseInt(countStr);
        if (currentCount >= otpProperties.getMaxResendCount()) {
            throw new AuthException(ErrorCode.OTP_SEND_TOO_MANY);
        }

        // 4. 새 OTP 발송 시 이전 실패 횟수 초기화
        // 유저가 재발송 요청을 하면 기존 시도 횟수를 리셋해서 새 OTP로 다시 5번 시도 가능하게 함
        String attemptsKey = OtpRedisKeyUtil.attemptsKey(email);
        redisTemplate.delete(attemptsKey);

        // 5. OTP 생성 및 Redis 저장
        String otpCode = OtpGenerator.generator();
        String otpKey = OtpRedisKeyUtil.otpCodeKey(email);
        redisTemplate.opsForValue().set(
                otpKey,
                otpCode,
                Duration.ofSeconds(otpProperties.getExpireSeconds())
        );

        // 7. 발송 횟수 증가 및 TTL 설정
        if (currentCount == 0) {
            redisTemplate.opsForValue().set(
                    resendCountKey,
                    "1",
                    Duration.ofSeconds(otpProperties.getResendWindowSeconds())
            );
        } else {
            redisTemplate.opsForValue().increment(resendCountKey);
        }

        // 8. 이메일 비동기 발송
        otpService.sendOtp(email, otpCode);

        // 9. 응답 반환
        return new OtpResponseDto(otpProperties.getExpireSeconds());
    }

    // ======== OTP 검증 ======================
    @Override
    public OtpVerifyResponseDto verifyEmailOtp(OtpVerifyRequestDto request, HttpServletResponse response) {
        String email = request.getEmail();

        // 1단계: 시도 횟수 확인
        // 브루트포스 방어: 5회 초과 시 잠금, 새 OTP 발급 유도
        String attemptsKey = OtpRedisKeyUtil.attemptsKey(email);
        String attemptsStr = redisTemplate.opsForValue().get(attemptsKey);
        int attempts = (attemptsStr == null) ? 0 : Integer.parseInt(attemptsStr);

        if (attempts >= otpProperties.getMaxAttempts()) {
            throw new AuthException(ErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED);
        }

        // 2단계: Redis에서 저장된 OTP 조회
        String otpKey = OtpRedisKeyUtil.otpCodeKey(email);
        String storedOtp = redisTemplate.opsForValue().get(otpKey);

        // 3단계: OTP 만료 확인
        // Redis TTL이 지나면 키가 자동 삭제 → get()이 null 반환 = 만료
        if (storedOtp == null) {
            throw new AuthException(ErrorCode.OTP_EXPIRED);
        }

        // 4단계: OTP 코드 일치 확인
        if (!storedOtp.equals(request.getOtpCode())) {
            // 불일치: 시도 횟수 +1
            // increment: 키가 없으면 0→1, 있으면 기존값+1
            redisTemplate.opsForValue().increment(attemptsKey);

            // 첫 번째 틀림일 때만 TTL 설정 (OTP 유효시간과 동일하게)
            // 이미 TTL이 있으면 덮어쓰지 않음
            if (attempts == 0) {
                redisTemplate.expire(attemptsKey,
                        Duration.ofSeconds(otpProperties.getExpireSeconds()));
            }

            throw new AuthException(ErrorCode.OTP_CODE_MISMATCH);
        }

        // 5단계: 검증 성공 - 사용된 키 정리
        redisTemplate.delete(otpKey);      // OTP 재사용 방지
        redisTemplate.delete(attemptsKey); // 시도 횟수 초기화

        // 6단계: 이메일 도메인으로 학교 정보 조회
        String emailDomain = email.substring(email.indexOf("@") + 1);
        UniversityResponseDto university = universityInternalService.getUniversityByDomain(emailDomain);

        // 7단계: signup_token 생성 (기존 JwtProvider 재사용)
        // type: "SIGNUP", TTL: 15분 (application.yml jwt.signup-token-validity-time)
        String signupToken = jwtProvider.generateSignupToken(email);

        // 8단계: signup_token HttpOnly 쿠키 발급
        // Path를 /api/v1/auth/signup으로 제한 → 다른 경로 요청 시엔 쿠키 미전송
        Cookie signupCookie = new Cookie("signup_token", signupToken);
        signupCookie.setHttpOnly(true);              // JS 접근 차단 (XSS 방어)
        signupCookie.setSecure(true);                // HTTPS에서만 전송
        signupCookie.setPath("/api/v1/auth/signup"); // 회원가입 엔드포인트에만 자동 전송
        signupCookie.setMaxAge(15 * 60);             // 15분
        response.addCookie(signupCookie);

        // 9단계: 응답 반환 (쿠키 외 body에는 학교 정보만)
        return new OtpVerifyResponseDto(university.universityId(), university.universityName());
    }

    // ======== 회원가입 ========
    @Override
    public SignupResponseDto signup(SignupRequestDto request,
                                    HttpServletRequest httpRequest,
                                    HttpServletResponse httpResponse) {

        // ===== 1단계: 쿠키에서 signup_token 추출 =====
        // 브라우저가 OTP 검증 시 받은 쿠키를 자동으로 이 요청에 담아서 전송
        String signupToken = extractSignupToken(httpRequest);
        if (signupToken == null) {
            throw new AuthException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        // ===== 2단계: signup_token 유효성 검증 =====
        // 만료됐거나 위조된 토큰이면 false
        if (!jwtProvider.validateToken(signupToken)) {
            throw new AuthException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        // ===== 3단계: 토큰 타입이 SIGNUP인지 확인 =====
        // ACCESS 토큰으로 회원가입 시도하는 것을 차단
        if (!"SIGNUP".equals(jwtProvider.getTokenType(signupToken))) {
            throw new AuthException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        // ===== 4단계: signup_token에서 이메일 추출 =====
        // OTP 검증 시 generateSignupToken(email) 로 발급했으므로 subject = email
        String email = jwtProvider.getEmailFromToken(signupToken);

        // ===== 5단계: 이메일 재중복 확인 =====
        // OTP 발송 ~ 회원가입 사이에 동일 이메일로 먼저 가입한 경우 차단
        // Service-to-Service: UserService 통해 확인
        if (userInternalService.isEmailAlreadyRegistered(email)) {
            throw new AuthException(ErrorCode.AUTH_ALREADY_REGISTERED_EMAIL);
        }

        // ===== 6단계: 닉네임 중복 확인 =====
        // Service-to-Service: UserService 통해 확인 (UserRepository 직접 접근 금지)
        if (userInternalService.existsByNickname(request.getNickname())) {
            throw new AuthException(ErrorCode.AUTH_NICKNAME_DUPLICATED);
        }

        // ===== 7단계: 필수 약관 동의 확인 =====
        // REQUIRED_TERM_VERSIONS 중 agreed=true가 아닌 항목이 하나라도 있으면 차단
        boolean hasRefusedRequired = request.getTermAgreements().stream()
                // 필수 약관 버전만 필터링
                .filter(term -> authProperties.getRequiredTermVersions().contains(term.termVersion()))
                // 동의하지 않은(agreed가 null이거나 false) 항목 존재 여부 확인
                .anyMatch(term -> !Boolean.TRUE.equals(term.agreed()));

        if (hasRefusedRequired) {
            throw new AuthException(ErrorCode.REQUIRED_TERM_NOT_AGREED);
        }

        // ===== 8단계: universityId 조회 =====
        // Service-to-Service: UniversityService 통해 조회
        String emailDomain = email.substring(email.indexOf("@") + 1);
        UniversityResponseDto university = universityInternalService.getUniversityByDomain(emailDomain);

        // ===== 9단계: 비밀번호 암호화 =====
        // 암호화 책임은 AuthService에 있음
        // UserService에는 이미 암호화된 값을 전달 (UserService는 암호화 로직을 모름)
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // ===== 10단계: User 생성 + 포인트 지급 + PointTransaction 기록 =====
        // Service-to-Service: UserService 통해 처리
        // UserServiceImpl.createUser() 내부에서 단일 트랜잭션으로 세 가지를 모두 처리함
        User savedUser = userCommandService.createUser(
                email,
                encodedPassword,
                request.getName(),
                request.getNickname(),
                university.universityId(),
                request.getMajor(),
                request.getStudentNumber(),
                request.getBirthDate(),
                request.getGender()
        );

        // ===== 11단계: 약관 동의 이력 저장 =====
        // TermAgreement는 Auth 도메인 엔티티 → AuthService에서 직접 저장
        // agreed=true인 항목만 DB에 저장 (false는 저장하지 않음)
        List<TermAgreement> agreements = request.getTermAgreements().stream()
                .filter(term -> Boolean.TRUE.equals(term.agreed()))
                .map(term -> TermAgreement.builder()
                        .userId(savedUser.getId())
                        .termVersion(term.termVersion())
                        .build())
                .toList();

        termAgreementRepository.saveAll(agreements);

        // ===== 12단계: signup_token 쿠키 파기 =====
        // Max-Age=0 → 브라우저가 즉시 삭제
        expireSignupTokenCookie(httpResponse);

        // ===== 13단계: refresh_token 발급 (자동 로그인) =====
        String deviceId = UUID.randomUUID().toString();

        // deviceId 포함 Refresh Token 발급
        String refreshToken = jwtProvider.generateRefreshToken(savedUser.getEmail(), deviceId);

        // Redis key에 deviceId 포함
        redisTemplate.opsForValue().set(
                buildRefreshKey(savedUser.getEmail(), deviceId),  // ← "refresh:{email}:{deviceId}"
                refreshToken,
                Duration.ofMillis(jwtProvider.getRefreshTokenValidityTime())
        );

        // refresh_token HttpOnly 쿠키 발급
        addRefreshTokenCookie(httpResponse, refreshToken);
        addDeviceIdCookie(httpResponse, deviceId);

        // ===== 14단계: Access Token 발급 =====
        String accessToken = jwtProvider.generateAccessToken(savedUser.getEmail());

        // ===== 15단계: 응답 반환 =====
        return new SignupResponseDto(
                savedUser.getId(),
                savedUser.getNickname(),
                savedUser.getTotalPoint(),   // 가입 보너스 지급 후 잔액 (10,000)
                accessToken
        );
    }

    // ======== 로그인 ======================
    @Override
    public LoginResponseDto login(LoginRequestDto request, HttpServletResponse response) {

        try {
            // Spring Security의 AuthenticationManager를 통해 이메일/비밀번호 검증
            // 내부적으로 CustomUserDetailsService.loadUserByUsername() 호출 후 비밀번호 비교
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
        } catch (DisabledException e) {
            // DisabledException = 탈퇴 계정 시도
            throw new AuthException(ErrorCode.AUTH_LOGIN_FAIL);
        } catch (BadCredentialsException e) {
            // 이메일 또는 비밀번호가 틀린 경우
            throw new AuthException(ErrorCode.AUTH_LOGIN_FAIL);
        }

        // 인증 성공 → 유저 정보 조회
        User user = userInternalService.findByEmail(request.getEmail());

        // deviceId 발급
        String deviceId = UUID.randomUUID().toString();

        // Access Token 생성
        String accessToken = jwtProvider.generateAccessToken(user.getEmail());

        // Refresh Token 생성 시 deviceId 포함
        String refreshToken = jwtProvider.generateRefreshToken(user.getEmail(), deviceId);

        // Redis key: "refresh:{email}:{deviceId}"
        // 같은 이메일로 다른 디바이스 로그인 → 다른 deviceId → 기존 세션 유지됨
        redisTemplate.opsForValue().set(
                buildRefreshKey(user.getEmail(), deviceId),  // ← 변경: deviceId 포함 key
                refreshToken,
                Duration.ofMillis(jwtProvider.getRefreshTokenValidityTime())
        );

        // device_id 쿠키도 함께 발급
        // Refresh Token을 HttpOnly 쿠키로 응답에 추가
        addRefreshTokenCookie(response, refreshToken);
        addDeviceIdCookie(response, deviceId);

        return new LoginResponseDto(user.getId(), user.getNickname(), accessToken);
    }

    // ===== 토큰 재발급 =====
    @Override
    public TokenResponseDto refresh(String refreshToken, String deviceId, HttpServletResponse response) {

        // refreshToken null 체크
        if (refreshToken == null) {
            log.warn("[Auth Refresh] refresh_token 쿠키 없음");
            throw new AuthException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        // deviceId null 체크 (device_id 쿠키 없이 재발급 시도 차단)
        if (deviceId == null) {
            log.warn("[Auth Refresh] device_id 쿠키 없음");
            throw new AuthException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        // 1. 토큰 형식 및 서명 검증
        if (!jwtProvider.validateToken(refreshToken)) {
            log.warn("[Auth Refresh] refresh_token JWT 검증 실패");
            throw new AuthException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        // 2. 토큰 타입이 REFRESH인지 확인
        if (!"REFRESH".equals(jwtProvider.getTokenType(refreshToken))) {
            log.warn("[Auth Refresh] 토큰 타입 불일치");
            throw new AuthException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        // 3. 토큰에서 이메일 추출
        String email = jwtProvider.getEmailFromToken(refreshToken);

        // 4. Redis에서 해당 디바이스의 토큰 조회
        // key: "refresh:{email}:{deviceId}" → 이 디바이스의 세션만 검증
        String redisKey = buildRefreshKey(email, deviceId);
        String storedToken = redisTemplate.opsForValue().get(redisKey);
        if (!refreshToken.equals(storedToken)) {
            // 이미 사용된 토큰이거나 다른 디바이스의 토큰인 경우
            log.warn("[Auth Refresh] Redis 토큰 없음 또는 불일치 | email={}, deviceId={}, key={}",
                    email, deviceId, redisKey);
            throw new AuthException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        // 5. 새 Access Token만 발급한다. Refresh Token은 만료/로그아웃 전까지 유지한다.
        String newAccessToken = jwtProvider.generateAccessToken(email);

        String currentRefreshToken = refreshToken;

        // 6. 동일 토큰으로 TTL만 갱신해 도메인 환경에서 쿠키/Redis 불일치를 막는다.
        redisTemplate.opsForValue().set(
                buildRefreshKey(email, deviceId),  // 같은 key 유지
                currentRefreshToken,
                Duration.ofMillis(jwtProvider.getRefreshTokenValidityTime())
        );

        // 7. 쿠키 Max-Age도 Redis TTL과 맞춰 재전송한다.
        addRefreshTokenCookie(response, currentRefreshToken);

        return new TokenResponseDto(newAccessToken);
    }

    // ======== 로그아웃 ========
    @Override
    public void logout(String refreshToken, String deviceId, HttpServletResponse response) {
        if (refreshToken != null && jwtProvider.validateToken(refreshToken)) {
            String email = jwtProvider.getEmailFromToken(refreshToken);
            // "refresh:{email}:{deviceId}" key만 삭제 → 이 디바이스 세션만 로그아웃
            // 다른 기기는 계속 로그인 상태 유지
            redisTemplate.delete(buildRefreshKey(email, deviceId));
        }

        // refresh_token 쿠키 만료
        expireRefreshTokenCookie(response);

        // device_id 쿠키도 함께 만료
        expireDeviceIdCookie(response);
    }

    // ======== 회원 탈퇴 ========
    @Override
    public WithdrawResponseDto withdraw(
            Long userId, WithdrawRequestDto request, String refreshToken, HttpServletResponse response) {
        // 1. 비밀번호 검증 + 상태 변경
        userCommandService.withdrawUser(userId, request.getPassword());

        // 2. Redis에서 Refresh Token 삭제 - 로그아웃과 동일한 방식으로 토큰 무효화
        if (refreshToken != null && jwtProvider.validateToken(refreshToken)) {
            String email = jwtProvider.getEmailFromToken(refreshToken);
            redisTemplate.delete(REFRESH_TOKEN_KEY_PREFIX + email);
        }

        // 3. Refresh Token 쿠키 파기
        expireRefreshTokenCookie(response);

        // 4. 응답 변환
        return WithdrawResponseDto.from(userId);
    }

    // ===== private 헬퍼 메서드 =====

    // 쿠키 배열에서 특정 이름의 쿠키 값을 꺼냄
    // HttpServletRequest의 getCookies()는 배열을 반환하므로 스트림으로 탐색
    private String extractSignupToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> "signup_token".equals(cookie.getName()))
                .findFirst()
                .map(Cookie::getValue)
                .orElse(null);
    }

    // refresh_token 전용 HttpOnly 쿠키 추가 헬퍼
    private void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        addSecureCookie(
                response,
                "refresh_token",
                refreshToken,
                "/",
                Duration.ofMillis(jwtProvider.getRefreshTokenValidityTime())
        );
    }

    // 범용 쿠키 추가 헬퍼
    // cookieName: 쿠키 이름 / value: 쿠키 값 / path: 전송 경로 제한 / maxAge: 유효 시간(초)
    private void expireSignupTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie("signup_token", "");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/api/v1/auth/signup");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    // 쿠키 만료(삭제) 헬퍼
    // Max-Age=0으로 설정하면 브라우저가 즉시 해당 쿠키를 삭제
    private void expireRefreshTokenCookie(HttpServletResponse response) {
        addSecureCookie(response, "refresh_token", "", "/", Duration.ZERO);
    }

    // device_id 전용 HttpOnly 쿠키 추가 헬퍼
    private void addDeviceIdCookie(HttpServletResponse response, String deviceId) {
        addSecureCookie(
                response,
                DEVICE_ID_COOKIE_NAME,
                deviceId,
                "/",
                Duration.ofMillis(jwtProvider.getRefreshTokenValidityTime())
        );
    }

    // device_id 쿠키 만료 헬퍼
    private void expireDeviceIdCookie(HttpServletResponse response) {
        addSecureCookie(response, DEVICE_ID_COOKIE_NAME, "", "/", Duration.ZERO);
    }

    private void addSecureCookie(
            HttpServletResponse response,
            String name,
            String value,
            String path,
            Duration maxAge
    ) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)
                .path(path)
                .maxAge(maxAge)
                .sameSite("None")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    // Redis key 조합 헬퍼
    private String buildRefreshKey(String email, String deviceId) {
        return REFRESH_TOKEN_KEY_PREFIX + email + ":" + deviceId;
    }
}
