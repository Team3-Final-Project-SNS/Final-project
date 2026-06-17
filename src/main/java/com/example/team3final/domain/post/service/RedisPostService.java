package com.example.team3final.domain.post.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.post.cache.PostCachePolicy;
import com.example.team3final.domain.post.dto.response.GetPostsItemResponseDto;
import com.example.team3final.domain.post.enums.PostStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisPostService {

    // Redis에 문자열 기반 key/value를 저장하고 조회하기 위한 Spring Redis Template
    private final StringRedisTemplate stringRedisTemplate;
    // PageResponseDto<GetPostsItemResponseDto>를 JSON 문자열로 변환하고 다시 객체로 복원하기 위한 ObjectMapper
    private final ObjectMapper objectMapper;

    // 게시글 목록 조회 API 응답 캐시를 조회
    public Optional<PageResponseDto<GetPostsItemResponseDto>> getPostList(
            Long userId,
            PostStatus status,
            int page,
            int size,
            String sortKey
    ) {
        // 사용자, 상태, 페이지, 크기를 기준으로 Redis key를 생성
        String cacheKey = PostCachePolicy.postListKey(userId, status, page, size, sortKey);

        try {
            // Redis에서 캐시된 JSON 문자열을 조회
            String cachedValue = stringRedisTemplate.opsForValue().get(cacheKey);

            // Redis에 값이 없으면 Cache Miss -> Optional 반환
            if (cachedValue == null) {
                return Optional.empty();
            }

            // JSON 문자열을 PageResponseDto<GetPostsItemResponseDto> 타입으로 역직렬화
            PageResponseDto<GetPostsItemResponseDto> responseDto = objectMapper.readValue(
                    cachedValue,
                    new TypeReference<PageResponseDto<GetPostsItemResponseDto>>() {
                    }
            );

            // 캐시된 응답이 정상 복원되었으므로 Optional에 담아 반환
            return Optional.of(responseDto);

        } catch (JsonProcessingException e) {
            // JSON 역직렬화 실패 시 잘못 저장된 캐시일 수 있으므로 해당 key를 삭제
            stringRedisTemplate.delete(cacheKey);

            // 캐시 실패가 API 실패로 이어지면 안 되므로 DB 조회 흐름으로 fallback
            return Optional.empty();
        } catch (RuntimeException e) {
            // Redis 연결 오류 등 캐시 계층 문제가 발생해도 API 자체는 동작
            return Optional.empty();
        }
    }

    // 게시글 목록 조회 API 응답 결과를 Redis에 저장
    public void savePostList(
            Long userId,
            PostStatus status,
            int page,
            int size,
            String sortKey,
            PageResponseDto<GetPostsItemResponseDto> responseDto
    ) {
        // 사용자, 상태, 페이지, 크기를 기준으로 Redis Key를 생성
        String cacheKey = PostCachePolicy.postListKey(userId, status, page, size, sortKey);

        try {

            // 응답 객체를 JSON 문자열로 직렬화 합니다.
            String cachedValue = objectMapper.writeValueAsString(responseDto);

            // Redis에 JSON 문자열을 저장하고 TTL을 함께 설정
            stringRedisTemplate.opsForValue().set(
                    cacheKey,
                    cachedValue,
                    PostCachePolicy.POST_LIST_TTL
            );
        } catch (JsonProcessingException e) {
            // JSON 직렬화 실패는 캐시 저장 실패일 뿐, API 응답 실패로 전파 X
            // 원인 추적을 위해 로그를 남김
            log.warn("게시글 목록 캐시 저장 실패 - JSON 직렬화 실패 | key={}", cacheKey, e);
        } catch (RuntimeException e) {
            // Redis 장애가 게시글 목록 조회 API 장애로 번지지 않도록 예외는 전파 X
            // 운영/테스트 중 확인할 수 있도록 warn 로그 기록
            log.warn("게시글 목록 캐시 저장 실패 - Redis 처리 실패 | key={}", cacheKey, e);
        }
    }

    public void evictPostLists() {
        try {
            // 게시글 생성/변경 직후 목록이 오래된 Redis 응답을 보지 않도록 전체 목록 캐시를 비웁니다.
            Set<String> keys = stringRedisTemplate.keys(PostCachePolicy.POST_LIST_PREFIX + "*");
            if (keys == null || keys.isEmpty()) {
                return;
            }
            stringRedisTemplate.delete(keys);
        } catch (RuntimeException e) {
            log.warn("게시글 목록 캐시 삭제 실패", e);
        }
    }
}
