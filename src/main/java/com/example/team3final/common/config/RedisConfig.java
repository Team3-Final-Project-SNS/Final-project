package com.example.team3final.common.config;

import com.example.team3final.domain.chat.pubsub.RedisMessageSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;


@Configuration
public class RedisConfig {

    // 채팅방 ID별로 구분되는 채널 패턴 (예: chat:room:1)
    public static final String CHAT_CHANNEL_PATTERN = "chat:room:*";

    // StringRedisTemplate: Redis에 문자열을 저장 조회하는 도구
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        // RedisConnectionFactory: Spring이 appllication.yml 설정을 읽어서 자동으로 생성.
        return new StringRedisTemplate(connectionFactory);
    }

    // Redis Pub/Sub 메세지 리스너 컨테이너
    // Redis에서 메세지가 오면 자동으로 RedisMessageSubscriber에게 전달
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter listenerAdapter
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        // chat:room:* 패턴의 모든 채널 구독
        container.addMessageListener(listenerAdapter, new PatternTopic(CHAT_CHANNEL_PATTERN));
        return container;
    }

    // Redis 메세지를 받아서 RedisMessageSubscriber.onMessage()로 전달하는 어댑터
    @Bean
    public MessageListenerAdapter listenerAdapter(RedisMessageSubscriber subscriber) {
        return new MessageListenerAdapter(subscriber, "onMessage");
    }
}
