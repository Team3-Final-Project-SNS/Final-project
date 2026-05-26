package com.example.team3final.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 한끼팟 AI 설정을 Spring Bean으로 등록하는 설정 클래스입니다.
 *
 * @EnableConfigurationProperties(AiProperties.class)를 통해
 * application.yml / application-local.yml 의 app.ai.* 값을
 * AiProperties 객체로 주입받을 수 있게 합니다.
 *
 * 참고:
 * - spring.ai.openai.* 값은 Spring AI 자동 설정이 사용합니다.
 * - app.ai.* 값은 한끼팟의 매칭 AI, 고객센터 AI, 신고 요약 AI,
 *   채팅 검열 AI 서비스 코드에서 직접 사용합니다.
 */


@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {
}