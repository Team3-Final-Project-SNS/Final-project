package com.example.team3final.domain.auth.service;

import com.example.team3final.common.config.OtpProperties;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.ServiceException;
import com.example.team3final.common.jwt.JwtProvider;
import com.example.team3final.domain.auth.dto.request.LoginRequestDto;
import com.example.team3final.domain.auth.dto.request.OtpRequestDto;
import com.example.team3final.domain.auth.dto.response.LoginResponseDto;
import com.example.team3final.domain.auth.dto.response.OtpResponseDto;
import com.example.team3final.domain.auth.dto.response.TokenResponseDto;
import com.example.team3final.domain.auth.util.OtpGenerator;
import com.example.team3final.domain.auth.util.OtpRedisKeyUtil;
import com.example.team3final.domain.university.service.UniversityService;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

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

    private static final String REFRESH_TOKEN_KEY_PREFIX = "refresh:";

    // ======== OTP 발송 ======================
    @Override
    public OtpResponseDto sendEmailOtp(OtpRequestDto request) {
        String email = request.email();

        // 1. .ac.kr 도메인 검증
        if (!email.endsWith(".ac.kr")) {
            throw new ServiceException(ErrorCode.INVALID_EMAIL_DOMAIN);
        }

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

    // ======== 로그인 ======================
    @Override
    public LoginResponseDto login(LoginRequestDto request, HttpServletResponse response) {

        try {
            // Spring Security의 AuthenticationManager를 통해 이메일/비밀번호 검증
            // 내부적으로 CustomUserDetailsService.loadUserByUsername() 호출 후 비밀번호 비교
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (DisabledException e) {
            // CustomUserDetailsService에서 disabled=true로 설정된 경우 (정지/탈퇴 계정)
            throw new ServiceException(ErrorCode.USER_SUSPENDED_OR_WITHDRAWN);
        } catch (BadCredentialsException e) {
            // 이메일 또는 비밀번호가 틀린 경우
            throw new ServiceException(ErrorCode.LOGIN_FAIL);
        }

        // 인증 성공 → 유저 정보 조회
        User user = userService.findByEmail(request.email());

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

    // ===== 1.6 로그아웃 =====
    @Override
    public void logout(String refreshToken, HttpServletResponse response) {

        // 1. Refresh Token이 있으면 Redis에서 삭제 (서버측 무효화)
        if (refreshToken != null && jwtProvider.validateToken(refreshToken)) {
            String email = jwtProvider.getEmailFromToken(refreshToken);
            redisTemplate.delete(REFRESH_TOKEN_KEY_PREFIX + email);
        }

        // 2. 쿠키 만료 처리 (Max-Age=0으로 브라우저가 즉시 삭제하도록)
        expireRefreshTokenCookie(response);
    }

    // ===== 쿠키 생성 헬퍼 =====
    private void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie("refresh_token", refreshToken);
        cookie.setHttpOnly(true);       // JavaScript에서 접근 불가 (XSS 방어)
        cookie.setSecure(true);         // HTTPS에서만 전송
        cookie.setPath("/");            // 모든 경로에서 쿠키 전송
        cookie.setMaxAge(14 * 24 * 60 * 60); // 14일 (초 단위)
        response.addCookie(cookie);
    }

    // ===== 쿠키 만료 헬퍼 =====
    private void expireRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie("refresh_token", "");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);  // 즉시 만료
        response.addCookie(cookie);
    }
}
