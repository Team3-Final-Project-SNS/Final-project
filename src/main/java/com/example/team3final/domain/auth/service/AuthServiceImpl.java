package com.example.team3final.domain.auth.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.ServiceException;
import com.example.team3final.domain.auth.dto.OtpRequestDto;
import com.example.team3final.domain.auth.dto.OtpResponseDto;
import com.example.team3final.domain.auth.util.OtpGenerator;
import com.example.team3final.domain.auth.util.OtpRedisKeyUtil;
import com.example.team3final.domain.university.service.UniversityService;
import com.example.team3final.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService{

    private final StringRedisTemplate redisTemplate;
    private final OtpService otpService;
    private final UniversityService universityService;
    private final UserService userService;
    private final OtpProperties otpProperties;
    //
    // API 명세서 1.1: POST /api/v1/auth/email/otp
    @Override
    public OtpResponseDto sendEmailOtp(OtpRequestDto request) {
        String email = request.getEmail();

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
        String otpCode = OtpGenerator.generate();
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
        emailService.sendOtp(email, otpCode);

        // 10. 응답 반환
        return new EmailOtpResponseDto(otpProperties.getExpireSeconds());
    }
}
