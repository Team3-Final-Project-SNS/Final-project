package com.example.team3final.domain.chat.pubsub;

import com.example.team3final.common.kafka.KafkaIdempotencyService;
import com.example.team3final.common.kafka.KafkaTopics;
import com.example.team3final.domain.chat.dto.response.ChatMessageResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaChatMessageConsumer {

    // WebSocket 구독자들에게 메시지를 전달하는 도구
    private final SimpMessagingTemplate messagingTemplate;

    // JSON 문자열 -> DTO 변환 도구
    private final ObjectMapper objectMapper;

    // 멱등성 체크 서비스 주입
    private final KafkaIdempotencyService kafkaIdempotencyService;

    // Kafka chat-messages 토픽에서 메시지가 오면 자동으로 호출됨
    // KafkaChatMessageProducer -> Kafka -> 이 메서드 -> WebSocket 구독자들에게 전달
    @KafkaListener(
            topics = KafkaTopics.CHAT_MESSAGES,
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(String message) {
        try {
            // JSON 문자열 -> ChatMessageResponseDto 변환
            ChatMessageResponseDto response = objectMapper.readValue(message, ChatMessageResponseDto.class);

            // 이미 처리한 메시지인지 확인
            // messageId가 Redis에 있으면 중복 메시지 → 스킵
            // messageId가 Redis에 없으면 처음 수신 → 정상 처리 후 Redis에 기록
            if (!kafkaIdempotencyService.isFirstProcessing(String.valueOf(response.messageId()))) {
                return;
            }

            log.info("[Kafka Chat Consumer] 메시지 수신 - topic: {}, chatRoomId: {}, messageId: {}",
                    KafkaTopics.CHAT_MESSAGES, response.chatRoomId(), response.messageId());

            // WebSocket 구독자들에게 전달
            // /sub/chat/rooms/{chatRoomId} 구독 중인 모든 클라이언트에게 전송
            messagingTemplate.convertAndSend("/sub/chat/rooms/" + response.chatRoomId(), response);
        } catch (Exception e) {
            log.error("[Kafka Chat Consumer] 메시지 처리 실패 - error: {}", e.getMessage());
        }
    }
}