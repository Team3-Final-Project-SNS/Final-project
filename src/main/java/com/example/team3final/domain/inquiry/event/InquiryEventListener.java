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

    // @TransactionalEventListener: DB 트랜잭션 커밋이 완료된 후에만 실행됨
    // phase = AFTER_COMMIT: 커밋 성공 시에만 실행 (롤백 시 실행 안 됨)
    // @Async: Redis 업데이트를 별도 스레드에서 실행 (응답 지연 방지)
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleInquiryCreated(InquiryCreatedEvent event) {
        Long userId = event.userId();

        String dailyKey = DAILY_COUNT_KEY_PREFIX + userId;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midnight = LocalDate.now().plusDays(1).atStartOfDay();
        Duration timeUntilMidnight = Duration.between(now, midnight);

        // 하루 카운터 +1 (DB 커밋 성공 후에만 실행되므로 정합성 보장)
        Long newCount = stringRedisTemplate.opsForValue().increment(dailyKey);

        if (Long.valueOf(1).equals(newCount)) {
            stringRedisTemplate.expire(dailyKey, timeUntilMidnight);
        }

        // 쿨다운 키는 InquiryServiceImpl에서 직접 처리하므로 여기선 제거
    }
}