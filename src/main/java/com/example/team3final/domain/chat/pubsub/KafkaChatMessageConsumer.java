package com.example.team3final.domain.chat.pubsub;

import com.example.team3final.common.kafka.KafkaIdempotencyService;
import com.example.team3final.common.kafka.KafkaTopics;
import com.example.team3final.domain.chat.dto.response.ChatMessageResponseDto;
import com.example.team3final.domain.chat.repository.ChatMemberRepository;
import com.example.team3final.domain.user.service.UserService;
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

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final KafkaIdempotencyService kafkaIdempotencyService;
    private final ChatMemberRepository chatMemberRepository;
    private final UserService userService;

    @KafkaListener(
            topics = KafkaTopics.CHAT_MESSAGES,
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(String message) {

        // catch 블록에서 markFailed() 호출 시 필요하므로 messageId만 바깥 변수로 선언
        String messageId = null;

        // isFirstProcessing()이 true를 반환한 경우에만 markFailed 대상으로 표시
        // 주의: Redis가 null을 반환하는 예외적 케이스도 true로 처리되므로
        //       이게 "Redis 키가 실제로 등록됐다"를 100% 보장하진 않음.
        //       다만 이 경우 markFailed(=DEL)이 한 번 더 실행되는 정도이고
        //       없는 키 DEL은 no-op이라 기능상 문제없음
        boolean processingStarted = false;

        try {
            // JSON 문자열 -> ChatMessageResponseDto 변환 (try 내부 final 지역변수)
            ChatMessageResponseDto response = objectMapper.readValue(message, ChatMessageResponseDto.class);

            // catch에서 쓸 messageId만 바깥 변수에 기록
            messageId = String.valueOf(response.messageId());

            // 이미 처리한 메시지인지 확인
            // true: 처음 수신 -> Redis에 "처리 중" 키 등록 후 진행
            // false: 중복 메시지 -> 정상 종료 (DLT 안 감)
            if (!kafkaIdempotencyService.isFirstProcessing(messageId)) {
                return;
            }

            // 멱등성 체크에서 "처리 허용" 신호를 받음 -> 실패 시 markFailed 삭제 대상
            processingStarted = true;

            log.info("[Kafka Chat Consumer] 메시지 수신 - topic: {}, chatRoomId: {}, messageId: {}",
                    KafkaTopics.CHAT_MESSAGES, response.chatRoomId(), response.messageId());

            // NO_SHOW 멤버를 제외한 구독자에게만 개별 전송
            chatMemberRepository.findByChatRoomId(response.chatRoomId()).stream()
                    .filter(member -> !member.isNoShow())
                    .forEach(member -> {
                        // convertAndSendToUser는 Principal.getName() = email 기준으로 전달
                        String memberEmail = userService.getEmailByUserId(member.getUserId());
                        messagingTemplate.convertAndSendToUser(
                                memberEmail,
                                "/sub/chat/rooms/" + response.chatRoomId(),
                                response
                        );
                    });

        } catch (Exception e) {
            // 스택트레이스를 남겨야 원인 파악 가능 -> e.getMessage() 대신 e 전달
            log.error("[Kafka Chat Consumer] 메시지 처리 실패 - messageId: {}", messageId, e);

            // 멱등성 체크에서 처리 허용 신호를 받은 뒤 실패한 경우 키 삭제 시도
            // markFailed() 자체가 Redis 장애로 예외를 던지면
            // 원래 실패 원인(e)이 가려질 수 있으므로 별도 try-catch로 격리
            if (processingStarted) {
                try {
                    kafkaIdempotencyService.markFailed(messageId);
                } catch (Exception redisEx) {
                    log.error("[Kafka Chat Consumer] 멱등성 키 삭제 실패 - messageId: {}", messageId, redisEx);
                }
            }

            // 예외를 다시 던져야 DefaultErrorHandler 재시도(1초 x 3회) -> DLT 흐름이 동작함
            throw new RuntimeException("채팅 메시지 처리 실패", e);
        }
    }
}