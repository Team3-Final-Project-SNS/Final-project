package com.example.team3final.domain.post.cache;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

// 게시글 도메인에서 사용하는 캐시 이름을 관리
public class PostCachePolicy {

    private PostCachePolicy() {

    }

    // 게시글 목록 조회 캐시 키
    // Redis에는 이 cacheName을 prefix로 사용하여 key 생성
    // ex) post:list::user:8:status:OPEN:page:0:size:20
    public static final String POST_LIST = "post:list";

    // 게시글 목록 조회 TTL : 30초로 설정
    // 게시글 목록은 조회 빈도가 높지만, 게시글 등록, 모집 상태 변경, 신청자 수 변경 등에 따라 결과가 달라질 수 있음,
    // 따라서 너무 긴 TTL을 두면 오래된 목록이 노출될 수 있으므로, 30초로 짧게 선정
    public static final Duration POST_LIST_TTL = Duration.ofSeconds(30);

    // PageResponseDto, GetPostsItemResponseDto가 record 타입이기 때문에,
    // Redis에서 JSON을 다시 꺼낼 때 실제 타입 정보가 없으면 LinkedHashMap으로 복원될 수 있음,
    // post:list 캐시에만 타입 정보를 포함한 JSON 직렬화를 적용
    public static RedisCacheConfiguration postListConfig(RedisCacheConfiguration baseConfig) {

        ObjectMapper objectMapper = new ObjectMapper();

        // LocalDateTime, LocalDate, LocalTime 같은 Java Time 타입을 JSON으로 처리하기 위한 설정
        objectMapper.registerModule(new JavaTimeModule());

        // 날짜/시간 값을 timestamp 배열이 아니라 ISO-8601 문자열 형태로 저장
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Redis cache hit 시 JSON을 LinkedHashMap이 아니라 실제 DTO/record 타입으로 복원하기 위한 설정
        BasicPolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.example.team3final")
                .allowIfSubType("java.util")
                .allowIfSubType("java.time")
                .build();

        // record는 final 클래스이므로 NON_FINAL만 사용하면 타입 정보가 누락될 수 있으므로
        // 일반 객체와 컬렉션 타입 복원을 위해 NON_FINAL 범위에서 타입 정보를 포함
        objectMapper.activateDefaultTyping(
                typeValidator,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        GenericJackson2JsonRedisSerializer serializer
                = new GenericJackson2JsonRedisSerializer(objectMapper);

        return baseConfig
                .entryTtl(POST_LIST_TTL)
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(serializer)
                );
    }

}
