package com.example.team3final.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisConfig {

    // StringRedisTemplate: Redis에 문자열을 저장 조회하는 도구
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        // RedisConnectionFactory: Spring이 appllication.yml 설정을 읽어서 자동으로 생성.
        return new StringRedisTemplate(connectionFactory);
    }
}
