package com.example.team3final.domain.notification.cache;

import com.example.team3final.domain.notification.dto.response.GetUnreadCountResponseDto;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

// 알림 도메인 캐시 정책
// PostCachePolicy와 동일한 구조로 맞춤
public class NotificationCachePolicy {

    private NotificationCachePolicy() {}

    // 미확인 알림 카운트 캐시 키
    public static final String NOTIFICATION_UNREAD = "notification:unread:v2";

    // 미확인 알림 카운트 TTL: 10초
    // 벨 아이콘 숫자는 즉각 반영이 중요 → 매우 짧게
    public static final Duration NOTIFICATION_UNREAD_TTL = Duration.ofSeconds(10);

    /**
     * 미확인 알림 카운트 전용 캐시 설정
     *
     * GetUnreadCountResponseDto는 final record라 NON_FINAL 기본 타이핑 대상이 아니다.
     * 따라서 루트 타입을 명시하는 serializer를 사용해 @class 없이도 복원한다.
     */
    public static RedisCacheConfiguration notificationUnreadCacheConfig(Duration ttl) {
        ObjectMapper mapper = createNotificationObjectMapper();
        Jackson2JsonRedisSerializer<GetUnreadCountResponseDto> serializer =
                new Jackson2JsonRedisSerializer<>(mapper, GetUnreadCountResponseDto.class);

        return baseCacheConfig(ttl)
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(serializer)
                );
    }

    private static ObjectMapper createNotificationObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Java 8 날짜/시간 타입 직렬화 지원
        mapper.registerModule(new JavaTimeModule());
        // ISO-8601 문자열 형태로 직렬화
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // record 타입 역직렬화를 위해 타입 정보 포함
        // ex) {"@class":"com.example...GetNotificationsResponseDto", ...}
        mapper.activateDefaultTyping(
                mapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        return mapper;
    }

    private static RedisCacheConfiguration baseCacheConfig(Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                // null 값은 캐시에 저장하지 않음
                .disableCachingNullValues()
                // 캐시 키를 사람이 읽을 수 있는 문자열로 직렬화
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer())
                )
                .entryTtl(ttl);
    }
}
