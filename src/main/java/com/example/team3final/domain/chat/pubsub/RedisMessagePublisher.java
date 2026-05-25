package com.example.team3final.domain.chat.pubsub;

import com.example.team3final.domain.chat.dto.response.ChatMessageResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisMessagePublisher {

    // Redis에 문자열을 저장/발행하는 도구
    private final StringRedisTemplate stringRedisTemplate;

    //DTO -> JSON 문자열 변환 도구
    private final ObjectMapper objectMapper;

    // 채팅 메세지를 Redis 채널에 발행
    // ChatMessageHandler -> 이 메서드 -> Redis -> RedisMessageSubscriber -> WebSocket
    public void publish(Long chatRoomId, ChatMessageResponseDto response) {
        try {
            // DTO -> JSON 문자열 변환
            String message = objectMapper.writeValueAsString(response);

            // Redis 채널에 발행 (예: chat:room:1)
            String channel = "chat:room:" + chatRoomId;
            stringRedisTemplate.convertAndSend(channel, message);

            log.info("[Redis Publisher] 메세지 발행 - channel: {}, messageId: {}", channel, response.messageId());
        } catch (Exception e) {
            log.error("[Redis Publisher] 메세지 발행 실패 - chatRoomId: {}, error: {}", chatRoomId, e.getMessage());
        }
    }
}
