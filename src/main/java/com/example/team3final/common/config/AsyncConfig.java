package com.example.team3final.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
// @EnableAsync: @Async 어노테이션이 실제로 동작하게 활성화해주는 설정
@EnableAsync
public class AsyncConfig {
    // 별도 스레드풀 설정 없이 기본 Spring 스레드풀 사용
}
