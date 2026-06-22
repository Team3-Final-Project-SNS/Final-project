package com.example.team3final.domain.post.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.post.cache.PostCachePolicy;
import com.example.team3final.domain.post.dto.response.GetPostsItemResponseDto;
import com.example.team3final.domain.post.enums.PostStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisPostService 단위 테스트")
class RedisPostServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    @DisplayName("게시글 목록 캐시가 없으면 빈 Optional을 반환한다")
    void getPostList_shouldReturnEmptyWhenCacheMiss() {
        RedisPostService redisPostService = new RedisPostService(stringRedisTemplate, new ObjectMapper());
        String key = PostCachePolicy.postListKey(1L, PostStatus.OPEN, 0, 10, "UNSORTED");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key)).thenReturn(null);

        Optional<PageResponseDto<GetPostsItemResponseDto>> response =
                redisPostService.getPostList(1L, PostStatus.OPEN, 0, 10, "UNSORTED");

        assertThat(response).isEmpty();
    }

    @Test
    @DisplayName("게시글 목록 캐시 저장 중 직렬화 가능한 응답을 Redis에 저장한다")
    void savePostList_shouldStoreSerializedResponse() {
        RedisPostService redisPostService = new RedisPostService(stringRedisTemplate, new ObjectMapper());
        PageResponseDto<GetPostsItemResponseDto> responseDto = new PageResponseDto<>(List.of(), 0, 10, 0, 0, false);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        redisPostService.savePostList(1L, PostStatus.OPEN, 0, 10, "UNSORTED", responseDto);

        org.mockito.Mockito.verify(valueOperations).set(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.contains("\"content\""),
                org.mockito.ArgumentMatchers.eq(PostCachePolicy.POST_LIST_TTL)
        );
    }
}
