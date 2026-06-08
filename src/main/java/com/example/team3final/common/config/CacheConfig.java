package com.example.team3final.common.config;

import com.example.team3final.domain.notification.cache.NotificationCachePolicy;
import com.example.team3final.domain.post.cache.PostCachePolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.Map;

// Spring Cache 공용 설정 파일
// Redis를 직접 StringRedisTemplate로 사용하는 기능과 별개로,
// @Cacheable, @CacheEvict, @CachePut 같은 Spring Cache 추상화를 사용할 때
// 어떤 저장소와 어떤 정책을 사용할지 정의하는 설정
@Configuration
public class CacheConfig {

    // 기본 TTL값 -> 5분
    // 별도 TTL 정책을 지정하지 않은 캐시에 대해서는 TTL 5분으로 설정
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    // Redis 기반 CacheManager 등록
    // CacheManager는 @Cacheable이 붙은 메서드가 호출될 때
    // 캐시 조회, 저장, 만료 정책을 처리하는 핵심 Bean
    @Bean
    public RedisCacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {

        RedisCacheConfiguration defaultConfig = createDefaultJsonCacheConfig();

        return RedisCacheManager.builder(connectionFactory)
                // cacheDefaults() -> 별도 정책을 지정하지 않은 캐시에 적용되는 기본 TTL 5분
                .cacheDefaults(defaultConfig.entryTtl(DEFAULT_TTL))
                // withInitialCacheConfigurations() -> 캐시 이름별로 TTL 정책을 다르게 설정
                // 캐시를 추가할 경우 이 Map에 cacheName별 정책을 추가하여 사용
                .withInitialCacheConfigurations(Map.of(
                        // 게시글 캐시 (팀원)
                        PostCachePolicy.SAME_UNIVERSITY_USER_IDS,
                        jdkSerializedCacheConfig(
                                defaultConfig,
                                PostCachePolicy.SAME_UNIVERSITY_USER_IDS_TTL
                        ),
                        // 알림 목록 캐시 (블레어) - record 타입이라 타입 정보 포함 JSON 직렬화 사용
                        NotificationCachePolicy.NOTIFICATION_LIST,
                        NotificationCachePolicy.notificationCacheConfig(
                                NotificationCachePolicy.NOTIFICATION_LIST_TTL
                        ),
                        // 미확인 알림 카운트 캐시 (블레어)
                        NotificationCachePolicy.NOTIFICATION_UNREAD,
                        NotificationCachePolicy.notificationCacheConfig(
                                NotificationCachePolicy.NOTIFICATION_UNREAD_TTL
                        )
                ))
                .build();
    }

    // 기본 Redis Cache 설정
    // 대부분의 캐시는 JSON 형태로 저장해도 충분하므로,
    // GenericJackson2JsonRedisSerializer를 기본 직렬화 방식으로 사용
    private RedisCacheConfiguration createDefaultJsonCacheConfig() {
        ObjectMapper objectMapper = new ObjectMapper();
        // Java 8 날짜/시간 타입 직렬화 지원
        objectMapper.registerModule(new JavaTimeModule());
        // ISO-8601 문자열 형태로 저장
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);

        return RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer)
                );
    }

    // 타입 보존이 중요한 캐시에 사용하는 공용 설정
    // ex) List<Long> 같은 제네릭 컬렉션은 JSON 역직렬화 과정에서 Integer로 복원될 수 있음
    // JdkSerializationRedisSerializer를 사용하면 Java 객체 타입 정보를 보존할 수 있다.
    public static RedisCacheConfiguration jdkSerializedCacheConfig(
            RedisCacheConfiguration baseConfig, Duration ttl) {
        return baseConfig
                .entryTtl(ttl)
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new JdkSerializationRedisSerializer()
                        )
                );
    }
}