package com.example.team3final.common.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

// Kafka Consumer 멱등성 서비스
// 동일한 메시지가 중복으로 수신됐을 때 중복 처리를 방지한다.
// Redis의 setIfAbsent()를 사용해 이미 처리된 eventId는 다시 처리하지 않는다.
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaIdempotencyService {

    private final StringRedisTemplate redisTemplate;

    // Redis Key TTL: 24시간
    // Kafka 재시도/브로커 장애로 같은 메시지가 재전송될 수 있는 최대 시간을 감안한 값
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    // Redis Key 접두사
    private static final String KEY_PREFIX = "kafka:idempotency:";

    // 처음 수신된 메시지인지 확인하고 처리 시작 표시
    // setIfAbsent(): Key가 없으면 생성 후 true 반환 (처음 수신 → 정상 처리)
    //               Key가 있으면 아무것도 안 하고 false 반환 (중복 → 스킵)
    //               원자적으로 실행되므로 동시에 같은 eventId가 들어와도 하나만 처리됨
    public boolean isFirstProcessing(String eventId) {

        String key = KEY_PREFIX + eventId;

        // SET key value NX EX ttl
        // NX: Key가 없을 때만 설정 / EX: TTL 설정
        Boolean isFirst = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", IDEMPOTENCY_TTL);

        // Redis 연결 문제 등으로 null 반환 시 안전하게 처리 허용
        // false 반환 시 메시지 유실 위험이 있으므로 true로 처리
        if (isFirst == null) {
            log.warn("[Kafka 멱등성] Redis 응답 null - eventId: {} 처리 허용", eventId);
            return true;
        }

        if (!isFirst) {
            log.warn("[Kafka 멱등성] 중복 메시지 감지 - eventId: {} 스킵", eventId);
        }

        return isFirst;
    }

    // 처리 실패 시 멱등성 키 삭제
    // 키를 지워야 재시도(retry) 때 isFirstProcessing()이 다시 true를 반환해서
    // "이미 처리됨"으로 오인되지 않고 정상적으로 재처리됨
    public void markFailed(String eventId) {
        String key = KEY_PREFIX + eventId;
        redisTemplate.delete(key);
        log.warn("[Kafka 멱등성] 처리 실패 - 멱등성 키 삭제 - eventId: {}", eventId);
    }
}