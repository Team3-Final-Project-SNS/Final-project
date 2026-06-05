package com.example.team3final.common.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

// DLQ/DLT 메시지 처리 Consumer
// Producer 전송 최종 실패(DLQ) 또는 Consumer 처리 최종 실패(DLT) 메시지를 수신
// 현재는 로그만 찍고 추후 알림 모니터링 연동 예정
@Slf4j
@Component
public class DlqEventConsumer {

    // 알림 DLT Consumer
    // NotificationEventConsumer가 3번 재시도 후 최종 실패한 메시지 수신
    @KafkaListener(
            topics = KafkaTopics.NOTIFICATIONS_DLT,
            groupId = "${spring.kafka.consumer.group-id}-dlt"
            // groupId를 기존과 다르게 설정 → DLT Consumer는 별도 그룹으로 관리
    )
    public void consumeNotificationDlt(@Payload String message,
                                       ConsumerRecord<String, String> record) {
        // DLT 메시지는 운영팀이 수동으로 재처리하거나
        // 추후 자동 재처리 로직 추가 가능
        log.error("[Kafka DLT] 알림 이벤트 최종 처리 실패 - " +
                        "topic: {}, partition: {}, offset: {}, message: {}",
                record.topic(),
                record.partition(),
                record.offset(),
                message);
    }

    // 채팅 DLT Consumer
    // ChatMessageConsumer가 3번 재시도 후 최종 실패한 메시지 수신
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

    // 알림 DLQ Consumer
    // Producer가 Kafka 전송을 최종 실패했을 때 수동으로 DLQ에 저장한 메시지 수신
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
    // Producer가 Kafka 전송을 최종 실패했을 때 수동으로 DLQ에 저장한 메시지 수신
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