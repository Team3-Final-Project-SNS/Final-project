package com.example.team3final.domain.notification.cache;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

// 알림 도메인 캐시 정책
// PostCachePolicy와 동일한 구조로 맞춤
public class NotificationCachePolicy {

    private NotificationCachePolicy() {}

    // 알림 목록 캐시 키
    public static final String NOTIFICATION_LIST = "notification:list:v2";
    // 미확인 알림 카운트 캐시 키
    public static final String NOTIFICATION_UNREAD = "notification:unread:v2";

    // 알림 목록 TTL: 30초
    // @CacheEvict로 즉시 무효화하므로 30초면 충분
    public static final Duration NOTIFICATION_LIST_TTL = Duration.ofSeconds(30);
    // 미확인 알림 카운트 TTL: 10초
    // 벨 아이콘 숫자는 즉각 반영이 중요 → 매우 짧게
    public static final Duration NOTIFICATION_UNREAD_TTL = Duration.ofSeconds(10);

    /**
     * 알림 도메인 전용 캐시 설정
     *
     * 알림 응답이 record + 제네릭 타입(CursorResponseDto<GetNotificationsResponseDto>)이라
     * 역직렬화 시 타입 정보가 필요함
     * → activateDefaultTyping으로 JSON에 @class 필드 포함
     *
     * 팀원 공용 CacheConfig의 createDefaultJsonCacheConfig()와 달리
     * 알림 도메인에만 적용되는 전용 설정
     */
    public static RedisCacheConfiguration notificationCacheConfig(Duration ttl) {
        ObjectMapper mapper = new ObjectMapper();
        // Java 8 날짜/시간 타입 직렬화 지원
        mapper.registerModule(new JavaTimeModule());
        // ISO-8601 문자열 형태로 직렬화
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // record 타입 역직렬화를 위해 타입 정보 포함
        // ex) {"@class":"com.example...GetNotificationsResponseDto", ...}
        mapper.activateDefaultTyping(
                mapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.PROPERTY
        );

        return RedisCacheConfiguration.defaultCacheConfig()
                // null 값은 캐시에 저장하지 않음
                .disableCachingNullValues()
                // 캐시 키를 사람이 읽을 수 있는 문자열로 직렬화
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer())
                )
                // 타입 정보 포함 JSON 직렬화
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new GenericJackson2JsonRedisSerializer(mapper))
                )
                .entryTtl(ttl);
    }
}