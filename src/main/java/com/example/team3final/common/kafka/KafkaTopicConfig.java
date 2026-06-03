package com.example.team3final.common.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic chatMessagesTopic() {
        return TopicBuilder.name(KafkaTopics.CHAT_MESSAGES)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic notificationsTopic() {
        return TopicBuilder.name(KafkaTopics.NOTIFICATIONS)
                .partitions(1)
                .replicas(1)
                .build();
    }
}