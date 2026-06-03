package com.example.team3final.domain.chat.pubsub;

import com.example.team3final.common.kafka.KafkaTopics;
import com.example.team3final.domain.chat.dto.response.ChatMessageResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaChatMessageProducer {

    // Kafka에 String 메시지를 발행하는 도구
    private final KafkaTemplate<String, String> kafkaTemplate;

    // DTO -> JSON 문자열 변환 도구
    private final ObjectMapper objectMapper;

    // 채팅 메시지를 Kafka 토픽에 발행
    // ChatMessageHandler -> 이 메서드 -> Kafka -> KafkaChatMessageConsumer -> WebSocket
    public void publish(Long chatRoomId, ChatMessageResponseDto response) {
        try {
            // DTO -> JSON 문자열 변환
            String message = objectMapper.writeValueAsString(response);

            // Kafka key는 chatRoomId로 설정
            // 같은 채팅방 메시지는 같은 파티션에 들어가 순서가 유지될 가능성이 높아짐
            String key = String.valueOf(chatRoomId);

            // Kafka 토픽에 발행
            kafkaTemplate.send(KafkaTopics.CHAT_MESSAGES, key, message);

            log.info("[Kafka Chat Producer] 메시지 발행 - topic: {}, chatRoomId: {}, messageId: {}",
                    KafkaTopics.CHAT_MESSAGES, chatRoomId, response.messageId());
        } catch (Exception e) {
            log.error("[Kafka Chat Producer] 메시지 발행 실패 - chatRoomId: {}, error: {}",
                    chatRoomId, e.getMessage());
        }
    }
}