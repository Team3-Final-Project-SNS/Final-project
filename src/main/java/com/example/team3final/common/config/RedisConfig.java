package com.example.team3final.common.config;

import com.example.team3final.common.utils.RedisLuaScripts;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

@Configuration
public class RedisConfig {

    // StringRedisTemplate: Redis에 문자열을 저장 조회하는 도구
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        // RedisConnectionFactory: Spring이 application.yml 설정을 읽어서 자동으로 생성
        return new StringRedisTemplate(connectionFactory);
    }

    // ZSet 스케줄러 공통 Lua Script Bean
    // ZSet에서 현재 시각 이전 항목 조회 + 삭제를 원자적으로 처리
    // 앱 시작 시 1회만 파싱 → MeetReminderScheduler, ExtensionTimeoutScheduler, ChatRoomScheduler 공통 사용
    @Bean
    @SuppressWarnings("unchecked") // Java 제네릭 타입 소거로 인한 불가피한 형변환 경고 억제
    public DefaultRedisScript<List<String>> popReadyItemsScript() {
        DefaultRedisScript<List<String>> script = new DefaultRedisScript<>();
        script.setScriptText(RedisLuaScripts.POP_READY_ITEMS);
        script.setResultType((Class<List<String>>) (Class<?>) List.class);
        return script;
    }
}