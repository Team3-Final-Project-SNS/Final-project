package com.example.team3final.domain.notification.pubsub;

import com.example.team3final.common.kafka.KafkaIdempotencyService;
import com.example.team3final.common.kafka.KafkaTopics;
import com.example.team3final.domain.notification.dto.event.NotificationEvent;
import com.example.team3final.domain.notification.dto.response.GetNotificationsResponseDto;
import com.example.team3final.domain.notification.entity.Notification;
import com.example.team3final.domain.notification.repository.NotificationRepository;
import com.example.team3final.domain.notification.sse.SseEmitterRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final NotificationRepository notificationRepository;
    private final SseEmitterRepository sseEmitterRepository;
    private final ObjectMapper objectMapper;
    // 멱등성 체크 서비스 주입
    private final KafkaIdempotencyService kafkaIdempotencyService;

    // Kafka notifications 토픽에서 알림 이벤트가 오면 자동으로 호출됨
    // NotificationPublisherImpl -> Kafka -> 이 메서드 -> DB 저장 + SSE 전송
    @KafkaListener(
            topics = KafkaTopics.NOTIFICATIONS,
            groupId = "${spring.kafka.consumer.group-id}"
    )
    @Transactional
    public void consume(@Payload String message) {
        try {
            // JSON 문자열 -> NotificationEvent 변환
            NotificationEvent event = objectMapper.readValue(message, NotificationEvent.class);

            // 이미 처리한 이벤트인지 확인
            // eventId가 Redis에 있으면 중복 메시지 → 스킵
            // eventId가 Redis에 없으면 처음 수신 → 정상 처리 후 Redis에 기록
            if (!kafkaIdempotencyService.isFirstProcessing(event.eventId())) {
                return;
            }

            // DB에 알림 저장
            Notification notification = Notification.builder()
                    .receiverId(event.receiverId())
                    .type(event.type())
                    .title(event.title())
                    .content(event.content())
                    .relatedDomain(event.relatedDomain())
                    .relatedId(event.relatedId())
                    .build();
            notificationRepository.save(notification);

            // SSE로 실시간 전송
            // 연결된 유저가 없으면 DB 저장만 하고 전송은 건너뜀
            sseEmitterRepository.findByUserId(event.receiverId()).ifPresent(emitter -> {
                try {
                    emitter.send(SseEmitter.event()
                            .name("notification")
                            .data(GetNotificationsResponseDto.from(notification)));
                } catch (IOException e) {
                    // 전송 실패 시 Emitter 삭제 (끊어진 연결 정리)
                    sseEmitterRepository.deleteByUserId(event.receiverId());
                    log.warn("[SSE] 알림 전송 실패 - userId: {}", event.receiverId());
                }
            });

            log.info("[Kafka Notification Consumer] 알림 처리 완료 - receiverId: {}, type: {}, notificationId: {}",
                    event.receiverId(), event.type(), notification.getId());
        } catch (Exception e) {
            log.error("[Kafka Notification Consumer] 알림 처리 실패 - error: {}", e.getMessage());
        }
    }
}