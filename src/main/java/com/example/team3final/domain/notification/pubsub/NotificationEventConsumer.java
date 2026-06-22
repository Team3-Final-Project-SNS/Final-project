package com.example.team3final.domain.notification.pubsub;

import com.example.team3final.common.kafka.KafkaIdempotencyService;
import com.example.team3final.common.kafka.KafkaTopics;
import com.example.team3final.domain.notification.dto.event.NotificationEvent;
import com.example.team3final.domain.notification.dto.response.GetNotificationsResponseDto;
import com.example.team3final.domain.notification.entity.Notification;
import com.example.team3final.domain.notification.enums.NotificationType;
import com.example.team3final.domain.notification.exception.InvalidNotificationEventException;
import com.example.team3final.domain.notification.repository.NotificationRepository;
import com.example.team3final.domain.notification.service.NotificationCacheService;
import com.example.team3final.domain.notification.sse.SseEmitterRepository;
import com.example.team3final.domain.notification.validation.NotificationEventValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
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
    private final NotificationCacheService notificationCacheService;
    // 멱등성 체크 서비스 주입
    private final KafkaIdempotencyService kafkaIdempotencyService;
    // Producer를 우회한 메시지도 저장 전에 동일한 정책으로 검증
    private final NotificationEventValidator notificationEventValidator;

    // Kafka notifications 토픽에서 알림 이벤트가 오면 자동으로 호출됨
    // NotificationPublisherImpl -> Kafka -> 이 메서드 -> DB 저장 + SSE 전송
    @KafkaListener(
            topics = KafkaTopics.NOTIFICATIONS,
            groupId = "${spring.kafka.consumer.group-id}"
    )
    @Transactional
    public void consume(@Payload String message) {

        // catch 블록에서 멱등성 키 삭제(markFailed)에 쓸 eventId
        // event 객체 자체는 try 안에서 선언해야 아래 람다(ifPresent)에서 effectively final로 캡처 가능
        String eventId = null;

        try {
            NotificationEvent event = deserialize(message);

            // Redis 멱등성 키를 만들기 전에 이벤트 전체를 검증한다.
            // eventId가 null인 이벤트가 kafka:idempotency:null로 등록되는 것을 방지한다.
            notificationEventValidator.validate(event);

            eventId = event.eventId();

            // 이미 처리한 이벤트인지 확인
            // eventId가 Redis에 있으면 중복 메시지 → 스킵
            // eventId가 Redis에 없으면 처음 수신 → 정상 처리 후 Redis에 기록
            if (!kafkaIdempotencyService.isFirstProcessing(eventId)) {
                return;
            }

            if (isNoShowNotification(event)
                    && notificationRepository.existsByReceiverTypeAndReceiverIdAndTypeAndRelatedDomainAndRelatedId(
                    event.receiverType(),
                    event.receiverId(),
                    event.type(),
                    event.relatedDomain(),
                    event.relatedId()
            )) {
                log.warn(
                        "[Kafka Notification Consumer] 중복 노쇼 알림 스킵 - receiverType: {}, receiverId: {}, type: {}, relatedId: {}",
                        event.receiverType(),
                        event.receiverId(),
                        event.type(),
                        event.relatedId()
                );
                return;
            }

            Notification notification = Notification.builder()
                    .receiverId(event.receiverId())
                    .receiverType(event.receiverType())
                    .type(event.type())
                    .title(event.title())
                    .content(event.content())
                    .relatedDomain(event.relatedDomain())
                    .relatedId(event.relatedId())
                    .build();

            // saveAndFlush(): INSERT를 즉시 실행시켜서 DB 제약조건 위반 등의 오류를
            // 이 메서드의 catch에서 바로 잡을 수 있게 함
            // (save()만 쓰면 INSERT가 트랜잭션 커밋 시점에 실행되어, 실패해도 이 catch가 못 잡음)
            notificationRepository.saveAndFlush(notification);

            notificationCacheService.evictAll();

            // SSE로 실시간 전송
            // 연결된 유저가 없으면 DB 저장만 하고 전송은 건너뜀
            sseEmitterRepository.find(event.receiverType(), event.receiverId()).ifPresent(emitter -> {
                try {
                    emitter.send(SseEmitter.event()
                            .name("notification")
                            .data(GetNotificationsResponseDto.from(notification)));
                } catch (IOException e) {
                    // 전송 실패 시 Emitter 삭제 (끊어진 연결 정리)
                    sseEmitterRepository.delete(event.receiverType(), event.receiverId());
                    log.warn("[SSE] 알림 전송 실패 - userId: {}", event.receiverId());
                }
            });

            log.info("[Kafka Notification Consumer] 알림 처리 완료 - receiverId: {}, type: {}, title: {}, notificationId: {}",
                    event.receiverId(), event.type(), event.title(), notification.getId());
            log.debug("[Kafka Notification Consumer] 알림 상세 내용 - title: {}, content: {}",
                    event.title(), event.content());

        } catch (InvalidNotificationEventException e) {
            // 데이터 자체가 잘못된 경우 재시도로 복구되지 않으므로 예외를 그대로 전달한다.
            log.error(
                    "[Kafka Notification Consumer] 유효하지 않은 알림 이벤트"
                            + " - eventId: {}, error: {}",
                    eventId,
                    e.getMessage(),
                    e
            );
            throw e;

        } catch (Exception e) {
            // 스택트레이스까지 포함해서 로깅 (마지막 인자로 예외 전달)
            log.error("[Kafka Notification Consumer] 알림 처리 실패 - eventId: {}", eventId, e);

            // 처리 실패 → 멱등성 키 삭제
            // 삭제 안 하면 재시도 때도 isFirstProcessing()이 false를 반환해서
            // "이미 처리됨"으로 오인되어 재처리도, DLT 전송도 일어나지 않음
            if (eventId != null) {
                try {
                    kafkaIdempotencyService.markFailed(eventId);
                } catch (Exception redisEx) {
                    // 멱등성 키 삭제 자체가 실패해도, 원본 예외(e)는 반드시 재던져야 하므로
                    // 여기서는 로그만 남기고 별도 처리하지 않음
                    log.error("[Kafka Notification Consumer] 멱등성 키 삭제 실패 - eventId: {}", eventId, redisEx);
                }
            }

            // 예외 재던짐
            // → @Transactional 롤백 (DB 부분 저장 방지)
            // → Spring Kafka DefaultErrorHandler가 1초 간격 3회 재시도
            // → 3회 모두 실패하면 DeadLetterPublishingRecoverer가 notifications.DLT로 발행
            //    → DltEventConsumer가 최종 실패 로그 기록
            throw new RuntimeException("알림 이벤트 처리 실패", e);
        }
    }

    private boolean isNoShowNotification(NotificationEvent event) {
        return event.type() == NotificationType.NO_SHOW_WARNING
                || event.type() == NotificationType.OPPONENT_NO_SHOW_WARNING
                || event.type() == NotificationType.NO_SHOW_CONFIRMED;
    }

    private NotificationEvent deserialize(String message) {
        try {
            return objectMapper.readValue(message, NotificationEvent.class);
        } catch (JsonProcessingException e) {
            // JSON 구조 또는 Enum 값이 잘못된 메시지도 재시도 없이 DLT로 보낸다.
            throw new InvalidNotificationEventException(
                    "알림 이벤트 역직렬화에 실패했습니다.",
                    e
            );
        }
    }
}
