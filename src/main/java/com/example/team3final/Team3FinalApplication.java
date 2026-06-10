package com.example.team3final;

import jakarta.annotation.PostConstruct;
import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@EnableJpaAuditing
@EnableConfigurationProperties
@EnableScheduling      // 스케줄러 활성화
@EnableAsync           // 알림 발송 (NotificationPublisher) 비동기 처리 활성화
@EnableCaching         // 캐시 사용 활성화
@SpringBootApplication(
        exclude = {
                // Spring AI가 자동으로 만들려는 vectorStore Bean을 비활성화
                // → 우리가 AiRagVectorStoreConfig에서 직접 만드는 Bean과 충돌 방지
                PgVectorStoreAutoConfiguration.class
        }
)
public class Team3FinalApplication {

    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
    }

    public static void main(String[] args) {
        SpringApplication.run(Team3FinalApplication.class, args);
    }

}
