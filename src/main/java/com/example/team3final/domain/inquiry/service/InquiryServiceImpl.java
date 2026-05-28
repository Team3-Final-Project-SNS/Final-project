package com.example.team3final.domain.inquiry.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.InquiryException;
import com.example.team3final.domain.inquiry.dto.request.CreateInquiryRequestDto;
import com.example.team3final.domain.inquiry.dto.response.CreateInquiryResponseDto;
import com.example.team3final.domain.inquiry.entity.Inquiry;
import com.example.team3final.domain.inquiry.repository.InquiryRepository;
import com.example.team3final.domain.user.service.UserService;
import jdk.jfr.Registered;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InquiryServiceImpl implements InquiryService{

    private final InquiryRepository inquiryRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserService userService;

    private static final int MAX_DAILY_INQUIRY_COUNT = 5; // 하루 최대 문의 접수 횟수
    private static final Duration COOLDOWN_DURATION =  Duration.ofMinutes(1); // 문의 간 최소 대기 시간

    private static final String DAILY_COUNT_KEY_PREFIX = "inquiry:daily:";
    private static final String COOLDOWN_KEY_PREFIX = "inquiry:cooldown:";

    // 고객 문의 접수
    @Override
    @Transactional
    public CreateInquiryResponseDto createInquiry(Long userId, CreateInquiryRequestDto request) {

        // 유저 존재 여부 확인
        userService.getUserInfo(userId);

        // 1분 쿨다운 확인
        validateCooldown(userId);

        // 하루 5개 제한 확인
        validateDailyLimit(userId);

        // 검증 완료 문의 엔티티 생성
        Inquiry inquiry = Inquiry.builder()
                .userId(userId)
                .title(request.getTitle())
                .content(request.getContent())
                .inquiryType(request.getType())
                .build();

        // DB에 저장
        Inquiry savedInquiry = inquiryRepository.save(inquiry);

        return CreateInquiryResponseDto.from(savedInquiry);
    }

    // ===== private 검증 메서드 =====

    // 1분 쿨다운 검증
    private void validateCooldown(Long userId) {
        String cooldownKey = COOLDOWN_KEY_PREFIX + userId;
        Boolean hasCooldown = stringRedisTemplate.hasKey(cooldownKey);
        if (Boolean.TRUE.equals(hasCooldown)) {
            throw new InquiryException(ErrorCode.INQUIRY_COOLDOWN);
        }
    }

    // 하루 5개 제한
    private void validateDailyLimit(Long userId) {
        String dailyKey = DAILY_COUNT_KEY_PREFIX + userId;
        String countStr = stringRedisTemplate.opsForValue().get(dailyKey);
        int count = (countStr == null) ? 0 : Integer.parseInt(countStr);
        if (count >= MAX_DAILY_INQUIRY_COUNT) {
            throw new InquiryException(ErrorCode.INQUIRY_DAILY_LIMIT_EXCEEDED);
        }
    }

    // 문의 접수 성공 후 Redis 업데이트 : daily 카운트 +1, 쿨다운 키 생성
    private void updateRedisAfterCreate(Long userId) {
        String dailyKey = DAILY_COUNT_KEY_PREFIX + userId;
        String cooldownKey = COOLDOWN_KEY_PREFIX + userId;

        // 오늘 자정까지 남은 시간 계산 (daily 키 TTL로 사용)
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midnight = LocalDate.now().plusDays(1).atStartOfDay(); // 내일 00:00:00
        Duration timeUntilMidnight = Duration.between(now, midnight);

        // 하루 카운트 +1
        Long newCount = stringRedisTemplate.opsForValue().increment(dailyKey);

        // 첫 문의일 때만 TTL 설정 (기존 키에 TTL 덮어쓰기 방지)
        if (Long.valueOf(1).equals(newCount)) {
            stringRedisTemplate.expire(dailyKey, timeUntilMidnight);
        }

        // 쿨다운 키 생성 (TTL 1분)
        stringRedisTemplate.opsForValue().set(cooldownKey, "1", COOLDOWN_DURATION);
    }
}
