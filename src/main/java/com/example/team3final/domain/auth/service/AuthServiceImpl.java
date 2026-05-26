package com.example.team3final.domain.auth.service;

import com.example.team3final.common.config.OtpProperties;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.ServiceException;
import com.example.team3final.common.jwt.JwtProvider;
import com.example.team3final.domain.auth.dto.request.LoginRequestDto;
import com.example.team3final.domain.auth.dto.request.OtpRequestDto;
import com.example.team3final.domain.auth.dto.request.OtpVerifyRequestDto;
import com.example.team3final.domain.auth.dto.request.SignupRequestDto;
import com.example.team3final.domain.auth.dto.response.*;
import com.example.team3final.domain.auth.util.OtpGenerator;
import com.example.team3final.domain.auth.util.OtpRedisKeyUtil;
import com.example.team3final.domain.university.dto.response.UniversityResponseDto;
import com.example.team3final.domain.university.service.UniversityService;
import com.example.team3final.domain.user.entity.TermAgreement;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.repository.TermAgreementRepository;
import com.example.team3final.domain.user.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
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

@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService{

    private final StringRedisTemplate redisTemplate;
    private final OtpService otpService;
    private final UniversityService universityService;
    private final UserService userService;
    private final OtpProperties otpProperties;
    private final JwtProvider jwtProvider;
    private final AuthenticationManager authenticationManager;
    private final TermAgreementRepository termAgreementRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String REFRESH_TOKEN_KEY_PREFIX = "refresh:";
    private static final int MAX_OTP_ATTEMPTS = 5;

    private static final List<String> REQUIRED_TERM_VERSIONS = List.of(
            "v1.0-service",
            "v1.0-privacy",
            "v1.0-location",  // 추가된 위치 정보 약관
            "v1.0-marketing"  // 추가된 마케팅 약관
    );

    // ======== OTP 발송 ======================
    @Override
    public OtpResponseDto sendEmailOtp(OtpRequestDto request) {
        String email = request.getEmail();

//        // 1. .ac.kr 도메인 검증
//        if (!email.endsWith(".ac.kr")) {
//            throw new ServiceException(ErrorCode.INVALID_EMAIL_DOMAIN);
//        }

        // 2. 등록된 학교 도메인인지 검증 (Service to Service)
        String emailDomain = email.substring(email.indexOf("@") + 1);
        boolean isRegisteredUniversity = universityService.isRegisteredActiveUniversity(emailDomain);
        if (!isRegisteredUniversity) {
            throw new ServiceException(ErrorCode.UNREGISTERED_UNIVERSITY);
        }

        // 3. 이미 가입된 이메일인지 검증 (Service to Service)
        boolean isAlreadyRegistered = userService.isEmailAlreadyRegistered(email);
        if (isAlreadyRegistered) {
            throw new ServiceException(ErrorCode.ALREADY_REGISTERED_EMAIL);
        }

        // 4. 재발송 쿨다운 체크 (1분 내 재발송 불가)
        String cooldownKey = OtpRedisKeyUtil.cooldownKey(email);
        Boolean isCooldown = redisTemplate.hasKey(cooldownKey);
        if (isCooldown) {
            throw new ServiceException(ErrorCode.OTP_COOLDOWN);
        }

        // 5. 시간 내 최대 재발송 횟수 체크 (1시간 내 3회)
        String resendCountKey = OtpRedisKeyUtil.resendCountKey(email);
        String countStr = redisTemplate.opsForValue().get(resendCountKey);
        int currentCount = (countStr == null) ? 0 : Integer.parseInt(countStr);
        if (currentCount >= otpProperties.getMaxResendCount()) {
            throw new ServiceException(ErrorCode.OTP_SEND_TOO_MANY);
        }

        // 6. OTP 생성 및 Redis 저장
        String otpCode = OtpGenerator.generator();
        String otpKey = OtpRedisKeyUtil.otpCodeKey(email);
        redisTemplate.opsForValue().set(
                otpKey,
                otpCode,
                Duration.ofSeconds(otpProperties.getExpireSeconds())
        );

        // 7. 쿨다운 키 저장 (1분 동안 재발송 불가)
        redisTemplate.opsForValue().set(
                cooldownKey,
                "1",
                Duration.ofSeconds(otpProperties.getCooldownSeconds())
        );

        // 8. 발송 횟수 증가 및 TTL 설정
        if (currentCount == 0) {
            redisTemplate.opsForValue().set(
                    resendCountKey,
                    "1",
                    Duration.ofSeconds(otpProperties.getResendWindowSeconds())
            );
        } else {
            redisTemplate.opsForValue().increment(resendCountKey);
        }

        // 9. 이메일 비동기 발송
        otpService.sendOtp(email, otpCode);

        // 10. 응답 반환
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

        if (attempts >= MAX_OTP_ATTEMPTS) {
            throw new ServiceException(ErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED);
        }

        // 2단계: Redis에서 저장된 OTP 조회
        String otpKey = OtpRedisKeyUtil.otpCodeKey(email);
        String storedOtp = redisTemplate.opsForValue().get(otpKey);

        // 3단계: OTP 만료 확인
        // Redis TTL이 지나면 키가 자동 삭제 → get()이 null 반환 = 만료
        if (storedOtp == null) {
            throw new ServiceException(ErrorCode.OTP_EXPIRED);
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

            throw new ServiceException(ErrorCode.OTP_CODE_MISMATCH);
        }

        // 5단계: 검증 성공 - 사용된 키 정리
        redisTemplate.delete(otpKey);      // OTP 재사용 방지
        redisTemplate.delete(attemptsKey); // 시도 횟수 초기화

        // 6단계: 이메일 도메인으로 학교 정보 조회
        String emailDomain = email.substring(email.indexOf("@") + 1);
        UniversityResponseDto university = universityService.getUniversityByDomain(emailDomain);

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
    @Transactional
    public SignupResponseDto signup(SignupRequestDto request,
                                    HttpServletRequest httpRequest,
                                    HttpServletResponse httpResponse) {

        // ===== 1단계: 쿠키에서 signup_token 추출 =====
        // 브라우저가 OTP 검증 시 받은 쿠키를 자동으로 이 요청에 담아서 전송
        String signupToken = extractSignupToken(httpRequest);
        if (signupToken == null) {
            throw new ServiceException(ErrorCode.INVALID_TOKEN);
        }

        // ===== 2단계: signup_token 유효성 검증 =====
        // 만료됐거나 위조된 토큰이면 false
        if (!jwtProvider.validateToken(signupToken)) {
            throw new ServiceException(ErrorCode.INVALID_TOKEN);
        }

        // ===== 3단계: 토큰 타입이 SIGNUP인지 확인 =====
        // ACCESS 토큰으로 회원가입 시도하는 것을 차단
        if (!"SIGNUP".equals(jwtProvider.getTokenType(signupToken))) {
            throw new ServiceException(ErrorCode.INVALID_TOKEN);
        }

        // ===== 4단계: signup_token에서 이메일 추출 =====
        // OTP 검증 시 generateSignupToken(email) 로 발급했으므로 subject = email
        String email = jwtProvider.getEmailFromToken(signupToken);

        // ===== 5단계: 이메일 재중복 확인 =====
        // OTP 발송 ~ 회원가입 사이에 동일 이메일로 먼저 가입한 경우 차단
        // Service-to-Service: UserService 통해 확인
        if (userService.isEmailAlreadyRegistered(email)) {
            throw new ServiceException(ErrorCode.ALREADY_REGISTERED_EMAIL);
        }

        // ===== 6단계: 닉네임 중복 확인 =====
        // Service-to-Service: UserService 통해 확인 (UserRepository 직접 접근 금지)
        if (userService.existsByNickname(request.getNickname())) {
            throw new ServiceException(ErrorCode.NICKNAME_DUPLICATED);
        }

        // ===== 7단계: 필수 약관 동의 확인 =====
        // REQUIRED_TERM_VERSIONS 중 agreed=true가 아닌 항목이 하나라도 있으면 차단
        boolean hasRefusedRequired = request.getTermAgreements().stream()
                // 필수 약관 버전만 필터링
                .filter(term -> REQUIRED_TERM_VERSIONS.contains(term.termVersion()))
                // 동의하지 않은(agreed가 null이거나 false) 항목 존재 여부 확인
                .anyMatch(term -> !Boolean.TRUE.equals(term.agreed()));

        if (hasRefusedRequired) {
            throw new ServiceException(ErrorCode.REQUIRED_TERM_NOT_AGREED);
        }

        // ===== 8단계: universityId 조회 =====
        // Service-to-Service: UniversityService 통해 조회
        String emailDomain = email.substring(email.indexOf("@") + 1);
        UniversityResponseDto university = universityService.getUniversityByDomain(emailDomain);

        // ===== 9단계: 비밀번호 암호화 =====
        // 암호화 책임은 AuthService에 있음
        // UserService에는 이미 암호화된 값을 전달 (UserService는 암호화 로직을 모름)
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // ===== 10단계: User 생성 + 포인트 지급 + PointTransaction 기록 =====
        // Service-to-Service: UserService 통해 처리
        // UserServiceImpl.createUser() 내부에서 단일 트랜잭션으로 세 가지를 모두 처리함
        User savedUser = userService.createUser(
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
        String refreshToken = jwtProvider.generateRefreshToken(savedUser.getEmail());

        // Redis에 저장 (RTR: 재발급 시 교체, 로그아웃 시 삭제)
        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_KEY_PREFIX + savedUser.getEmail(),
                refreshToken,
                Duration.ofMillis(14L * 24 * 60 * 60 * 1000) // 14일 (ms 단위)
        );

        // refresh_token HttpOnly 쿠키 발급
        addRefreshTokenCookie(httpResponse, refreshToken);

        // ===== 14단계: Access Token 발급 =====
        String accessToken = jwtProvider.generateAccessToken(savedUser.getEmail());

        // ===== 15단계: 응답 반환 =====
        return new SignupResponseDto(
                savedUser.getId(),
                savedUser.getNickname(),
                savedUser.getPoint(),   // 가입 보너스 지급 후 잔액 (10,000)
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
            // CustomUserDetailsService에서 disabled=true로 설정된 경우 (정지/탈퇴 계정)
            throw new ServiceException(ErrorCode.USER_SUSPENDED_OR_WITHDRAWN);
        } catch (BadCredentialsException e) {
            // 이메일 또는 비밀번호가 틀린 경우
            throw new ServiceException(ErrorCode.LOGIN_FAIL);
        }

        // 인증 성공 → 유저 정보 조회
        User user = userService.findByEmail(request.getEmail());

        // Access Token 생성
        String accessToken = jwtProvider.generateAccessToken(user.getEmail());

        // Refresh Token 생성 후 Redis에 저장 (RTR을 위해 서버측 보관)
        // Key: "refresh:{email}", Value: refreshToken, TTL: 14일
        String refreshToken = jwtProvider.generateRefreshToken(user.getEmail());
        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_KEY_PREFIX + user.getEmail(),
                refreshToken,
                Duration.ofMillis(14L * 24 * 60 * 60 * 1000) // 14일
        );

        // Refresh Token을 HttpOnly 쿠키로 응답에 추가
        addRefreshTokenCookie(response, refreshToken);

        return new LoginResponseDto(user.getId(), user.getNickname(), accessToken);
    }

    // ===== 토큰 재발급 =====
    @Override
    public TokenResponseDto refresh(String refreshToken, HttpServletResponse response) {

        // 1. 토큰 형식 및 서명 검증
        if (!jwtProvider.validateToken(refreshToken)) {
            throw new ServiceException(ErrorCode.INVALID_TOKEN);
        }

        // 2. 토큰 타입이 REFRESH인지 확인
        if (!"REFRESH".equals(jwtProvider.getTokenType(refreshToken))) {
            throw new ServiceException(ErrorCode.INVALID_TOKEN);
        }

        // 3. 토큰에서 이메일 추출
        String email = jwtProvider.getEmailFromToken(refreshToken);

        // 4. Redis에 저장된 Refresh Token과 비교 (탈취된 토큰 사용 방지)
        String storedToken = redisTemplate.opsForValue().get(REFRESH_TOKEN_KEY_PREFIX + email);
        if (!refreshToken.equals(storedToken)) {
            // 이미 사용된 토큰이거나 로그아웃된 경우
            throw new ServiceException(ErrorCode.INVALID_TOKEN);
        }

        // 5. 새 Access Token + 새 Refresh Token 발급 (RTR: 기존 토큰 폐기)
        String newAccessToken = jwtProvider.generateAccessToken(email);
        String newRefreshToken = jwtProvider.generateRefreshToken(email);

        // 6. Redis 업데이트 (기존 토큰 덮어쓰기)
        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_KEY_PREFIX + email,
                newRefreshToken,
                Duration.ofMillis(14L * 24 * 60 * 60 * 1000)
        );

        // 7. 새 Refresh Token 쿠키로 재전송
        addRefreshTokenCookie(response, newRefreshToken);

        return new TokenResponseDto(newAccessToken);
    }

    // ======== 로그아웃 ========
    @Override
    public void logout(String refreshToken, HttpServletResponse response) {
        if (refreshToken != null && jwtProvider.validateToken(refreshToken)) {
            String email = jwtProvider.getEmailFromToken(refreshToken);
            redisTemplate.delete(REFRESH_TOKEN_KEY_PREFIX + email);
        }

        // 인자 2개 버전 호출 → logout은 path가 "/" 고정
        expireRefreshTokenCookie(response);
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
        Cookie cookie = new Cookie("refresh_token", refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(14 * 24 * 60 * 60); // 1209600초 (14일) 고정
        response.addCookie(cookie);
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
        Cookie cookie = new Cookie("refresh_token", "");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}
