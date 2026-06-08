package com.example.team3final.common.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    // ==================== 캐시 이름 상수 ====================
    // 캐시 이름을 상수로 관리 → 오타 방지 + 전체 캐시 목록 한눈에 파악 가능
    // @Cacheable(cacheNames = CacheConfig.NOTIFICATION_LIST) 형태로 사용
    // v2: final record까지 타입 정보를 저장하는 직렬화 형식으로 변경
    // 기존 형식의 Redis 값은 역직렬화할 수 없으므로 캐시 이름을 버전업해 분리한다.
    public static final String NOTIFICATION_LIST = "notification:list:v2";   // 알림 목록
    public static final String NOTIFICATION_UNREAD = "notification:unread:v2"; // 미확인 알림 카운트

    // ==================== 직렬화 설정 ====================

    /**
     * Redis에 저장할 때 사용할 ObjectMapper 생성
     *
     * Java 객체를 JSON으로 변환할 때 아래 문제가 발생:
     * 1. LocalDateTime → JSON 변환 시 기본 ObjectMapper는 배열로 출력 [2026,6,7,12,30]
     *    → JavaTimeModule 등록으로 "2026-06-07T12:30:00" 형태로 직렬화
     * 2. 역직렬화(JSON → 객체) 시 클래스 정보 없으면 타입을 모름
     *    → enableDefaultTyping으로 JSON에 클래스 이름도 같이 저장
     */
    private ObjectMapper cacheObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // LocalDateTime 등 Java 8 날짜/시간 타입 직렬화 지원
        mapper.registerModule(new JavaTimeModule());

        // LocalDateTime을 배열이 아닌 ISO-8601 문자열로 직렬화
        // ex) [2026,6,7] → "2026-06-07T12:30:00"
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 역직렬화 시 타입 정보를 포함 → JSON에 @class 필드가 추가됨
        // ex) {"@class":"com.example...GetNotificationsResponseDto", "notificationId":1, ...}
        // 알림 응답은 final record이므로 NON_FINAL을 사용하면 최상위 @class가 누락된다.
        mapper.activateDefaultTyping(
                mapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.PROPERTY
        );

        return mapper;
    }

    // ==================== 기본 캐시 설정 ====================

    /**
     * 모든 캐시에 공통 적용되는 기본 설정
     *
     * - 키: StringRedisSerializer → "notification:list::1:0:20" 형태 (사람이 읽을 수 있는 문자열)
     * - 값: GenericJackson2JsonRedisSerializer → Java 객체를 JSON으로 직렬화
     * - null 캐싱 비활성화: DB 조회 결과가 null이어도 캐시에 저장하지 않음
     *   → null을 캐싱하면 "없는 데이터"가 캐시에 고정되는 문제 발생
     * - TTL: 기본 60초 (캐시 이름별로 덮어쓰기 가능)
     */
    private RedisCacheConfiguration defaultCacheConfig() {
        return RedisCacheConfiguration.defaultCacheConfig()
                // 캐시 키를 문자열로 직렬화 (Redis에서 key가 사람이 읽을 수 있는 형태)
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer())
                )
                // 캐시 값을 JSON으로 직렬화 (타입 정보 포함)
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new GenericJackson2JsonRedisSerializer(cacheObjectMapper()))
                )
                // null 값은 캐시에 저장하지 않음
                .disableCachingNullValues()
                // 기본 TTL 60초
                .entryTtl(Duration.ofSeconds(60));
    }

    // ==================== CacheManager Bean ====================

    /**
     * Spring Cache ↔ Redis 연결 다리 역할
     *
     * @Cacheable, @CacheEvict 어노테이션이 동작하려면
     * CacheManager Bean이 반드시 등록되어 있어야 함
     *
     * withInitialCacheConfigurations(): 캐시 이름별로 TTL을 다르게 설정
     * - notification:list  → 30초 (알림 목록은 실시간성 중요, 짧게)
     * - notification:unread → 10초 (벨 아이콘 카운트, 매우 짧게)
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {

        // 캐시 이름별 개별 TTL 설정 맵
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // 알림 목록 캐시: 30초 TTL
        // 근거: 30초 이내 새 알림이 오더라도 @CacheEvict로 즉시 무효화하므로 30초면 충분
        cacheConfigurations.put(
                NOTIFICATION_LIST,
                defaultCacheConfig().entryTtl(Duration.ofSeconds(30))
        );

        // 미확인 알림 카운트 캐시: 10초 TTL
        // 근거: 벨 아이콘 숫자는 즉각 반영이 중요 → 매우 짧게
        cacheConfigurations.put(
                NOTIFICATION_UNREAD,
                defaultCacheConfig().entryTtl(Duration.ofSeconds(10))
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultCacheConfig())           // 기본 설정 (등록 안 된 캐시 이름에 적용)
                .withInitialCacheConfigurations(cacheConfigurations) // 캐시 이름별 개별 설정
                .build();
    }
}
