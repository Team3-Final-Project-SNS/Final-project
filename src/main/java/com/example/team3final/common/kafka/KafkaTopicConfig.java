package com.example.team3final.common.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    // ==================== 기존 토픽 ====================

    @Bean
    public NewTopic chatMessagesTopic() {
        return TopicBuilder.name(KafkaTopics.CHAT_MESSAGES)
                .partitions(2)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic notificationsTopic() {
        return TopicBuilder.name(KafkaTopics.NOTIFICATIONS)
                .partitions(2)
                .replicas(1)
                .build();
    }

    // ==================== [신규] 실패 대응 토픽 ====================

    // 알림 DLQ - Producer 전송 최종 실패 메시지 보관
    @Bean
    public NewTopic notificationsDlqTopic() {
        return TopicBuilder.name(KafkaTopics.NOTIFICATIONS_DLQ)
                .partitions(1)
                .replicas(1)
                .build();
    }

    // 채팅 DLQ - Producer 전송 최종 실패 메시지 보관
    @Bean
    public NewTopic chatMessagesDlqTopic() {
        return TopicBuilder.name(KafkaTopics.CHAT_MESSAGES_DLQ)
                .partitions(1)
                .replicas(1)
                .build();
    }

    // 알림 DLT - Consumer 처리 최종 실패 메시지 보관
    @Bean
    public NewTopic notificationsDltTopic() {
        return TopicBuilder.name(KafkaTopics.NOTIFICATIONS_DLT)
                .partitions(1)
                .replicas(1)
                .build();
    }

    // 채팅 DLT - Consumer 처리 최종 실패 메시지 보관
    @Bean
    public NewTopic chatMessagesDltTopic() {
        return TopicBuilder.name(KafkaTopics.CHAT_MESSAGES_DLT)
                .partitions(1)
                .replicas(1)
                .build();
    }
}