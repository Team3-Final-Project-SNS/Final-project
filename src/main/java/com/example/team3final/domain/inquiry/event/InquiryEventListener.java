package com.example.team3final.domain.inquiry.event;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class InquiryEventListener {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String DAILY_COUNT_KEY_PREFIX = "inquiry:daily:";
    private static final String COOLDOWN_KEY_PREFIX = "inquiry:cooldown:";
    private static final Duration COOLDOWN_DURATION = Duration.ofMinutes(1);

    // @TransactionalEventListener: DB 트랜잭션 커밋이 완료된 후에만 실행됨
    // phase = AFTER_COMMIT: 커밋 성공 시에만 실행 (롤백 시 실행 안 됨)
    // @Async: Redis 업데이트를 별도 스레드에서 실행 (응답 지연 방지)
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)

    // 문의 접수 성공 후 Redis 업데이트
    public void handleInquiryCreated(InquiryCreatedEvent event) {
        Long userId = event.userId();

        String dailyKey = DAILY_COUNT_KEY_PREFIX + userId;
        String cooldownKey = COOLDOWN_KEY_PREFIX + userId;

        // 자정까지 남은 시간 계산
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midnight = LocalDate.now().plusDays(1).atStartOfDay();
        Duration timeUntilMidnight = Duration.between(now, midnight);

        // 하루 카운터 +1
        Long newCount = stringRedisTemplate.opsForValue().increment(dailyKey);

        // 첫 문의일 때만 TTL 설정 (이후엔 덮어쓰지 않음)
        if (Long.valueOf(1).equals(newCount)) {
            stringRedisTemplate.expire(dailyKey, timeUntilMidnight);
        }

        // 쿨다운 키 생성 (TTL 1분)
        stringRedisTemplate.opsForValue().set(cooldownKey, "1", COOLDOWN_DURATION);
    }
}