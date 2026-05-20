package com.example.team3final;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
@EnableConfigurationProperties
@EnableScheduling      // 스케줄러 활성화
public class Team3FinalApplication {

    public static void main(String[] args) {
        SpringApplication.run(Team3FinalApplication.class, args);
    }

}
