package com.example.team3final.domain.chat.pubsub;

import com.example.team3final.domain.chat.dto.request.ChatMessageRequestDto;
import com.example.team3final.domain.chat.dto.response.ChatMessageResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisMessageSubscriber {

    // WebSocket 구독자들에게 메세지를 전달하는 도구
    private final SimpMessagingTemplate messagingTemplate;

    // JSON 문자열 -> DTO 변환 도구
    private final ObjectMapper objectMapper;

    // Redis 채널에서 메세지가 오면 자동으로 호출됨
    // Redis Publish -> 이 메서드 -> Websocket 구독자들에게 전달
    public void onMessage(String message, String channel) {
        try {
            // JSON 문자열 -> ChatMessageResponseDto 변환
            ChatMessageResponseDto response = objectMapper.readValue(message, ChatMessageResponseDto.class);

            // 채널 이름에서 chatRoomId 추출 (chat:room:1 -> 1)
            String chatRoomId = channel.replace("chat:room:", "");
            log.info("[Redis Subscriber] 메세지 수신 - channel: {}, messageId: {}", channel, response.messageId());

            // WebSocket 구독자들에게 전달
            // /sub/chat/rooms/{chatRoomId} 구독 중인 모든 클라이언트에게 전송
            messagingTemplate.convertAndSend("/sub/chat/rooms/" + chatRoomId, response);
        } catch (Exception e) {
            log.error("[Redis Subscriber] 메세지 처리 실패 - channel: {}, error: {}", channel, e.getMessage());
        }
    }
}
