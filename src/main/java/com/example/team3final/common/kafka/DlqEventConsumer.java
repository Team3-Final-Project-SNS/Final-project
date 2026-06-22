package com.example.team3final.common.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

// DLQ (Dead Letter Queue) 메시지 처리 Consumer
// Producer가 Kafka 전송을 최종 실패했을 때 수동으로 DLQ에 저장한 메시지 수신
// 현재는 로그만 찍고 추후 Grafana + Prometheus 모니터링 연동 예정
@Slf4j
@Component
public class DlqEventConsumer {

    // 알림 DLQ Consumer
    // NotificationPublisherImpl이 Kafka 전송 최종 실패 시 DLQ로 발행한 메시지 수신
    @KafkaListener(
            topics = KafkaTopics.NOTIFICATIONS_DLQ,
            groupId = "${spring.kafka.consumer.group-id}-dlq"
    )
    public void consumeNotificationDlq(@Payload String message,
                                       ConsumerRecord<String, String> record) {
        log.error("[Kafka DLQ] 알림 이벤트 Producer 전송 최종 실패 - " +
                        "topic: {}, partition: {}, offset: {}, message: {}",
                record.topic(),
                record.partition(),
                record.offset(),
                message);
    }

    // 채팅 DLQ Consumer
    // KafkaChatMessageProducer가 Kafka 전송 최종 실패 시 DLQ로 발행한 메시지 수신
    @KafkaListener(
            topics = KafkaTopics.CHAT_MESSAGES_DLQ,
            groupId = "${spring.kafka.consumer.group-id}-dlq"
    )
    public void consumeChatMessageDlq(@Payload String message,
                                      ConsumerRecord<String, String> record) {
        log.error("[Kafka DLQ] 채팅 메시지 Producer 전송 최종 실패 - " +
                        "topic: {}, partition: {}, offset: {}, message: {}",
                record.topic(),
                record.partition(),
                record.offset(),
                message);
    }
}