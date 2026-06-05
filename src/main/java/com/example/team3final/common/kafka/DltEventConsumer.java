package com.example.team3final.common.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

// DLT (Dead Letter Topic) 메시지 처리 Consumer
// Consumer가 메시지 처리를 3회 재시도 후 최종 실패했을 때 자동으로 DLT에 저장된 메시지 수신
// 현재는 로그만 찍고 추후 Grafana + Prometheus 모니터링 연동 예정
@Slf4j
@Component
public class DltEventConsumer {

    // 알림 DLT Consumer
    // NotificationEventConsumer가 3번 재시도 후 최종 실패한 메시지 수신
    @KafkaListener(
            topics = KafkaTopics.NOTIFICATIONS_DLT,
            groupId = "${spring.kafka.consumer.group-id}-dlt"
    )
    public void consumeNotificationDlt(@Payload String message,
                                       ConsumerRecord<String, String> record) {
        log.error("[Kafka DLT] 알림 이벤트 최종 처리 실패 - " +
                        "topic: {}, partition: {}, offset: {}, message: {}",
                record.topic(),
                record.partition(),
                record.offset(),
                message);
    }

    // 채팅 DLT Consumer
    // KafkaChatMessageConsumer가 3번 재시도 후 최종 실패한 메시지 수신
    @KafkaListener(
            topics = KafkaTopics.CHAT_MESSAGES_DLT,
            groupId = "${spring.kafka.consumer.group-id}-dlt"
    )
    public void consumeChatMessageDlt(@Payload String message,
                                      ConsumerRecord<String, String> record) {
        log.error("[Kafka DLT] 채팅 메시지 최종 처리 실패 - " +
                        "topic: {}, partition: {}, offset: {}, message: {}",
                record.topic(),
                record.partition(),
                record.offset(),
                message);
    }
}