package com.example.team3final.common.config;

import io.portone.sdk.server.payment.PaymentClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Slf4j
@Configuration
@EnableConfigurationProperties(PortOneProperties.class)
public class PortOneConfig {

    @Bean
    public PaymentClient paymentClient(PortOneProperties properties) {
        String apiSecret = resolveRequiredProperty(
                properties.apiSecret(),
                "PORTONE_API_SECRET",
                "portone.api-secret"
        );
        String storeId = resolveRequiredProperty(
                properties.storeId(),
                "PORTONE_STORE_ID",
                "VITE_PORTONE_STORE_ID",
                "portone.store-id"
        );

        log.info("[PortOne] PaymentClient initialized - storeId: {}", storeId);

        // PortOne V2 Java SDK의 PaymentClient 빈 등록
        // apiSecret: 서버가 PortOne API 호출 시 사용하는 인증키 (환경변수로 주입)
        // null: baseUrl 기본값 사용 (api.portone.io)
        // storeId: 상점 식별자 (환경변수로 주입)
        return new PaymentClient(
                apiSecret,
                "https://api.portone.io",  // ← apiBase 기본값 직접 명시
                storeId
        );
    }

    private String resolveRequiredProperty(String primaryValue, String... fallbackKeys) {
        if (StringUtils.hasText(primaryValue)) {
            return primaryValue;
        }

        for (String fallbackKey : fallbackKeys) {
            String fallbackValue = System.getProperty(fallbackKey);
            if (!StringUtils.hasText(fallbackValue)) {
                fallbackValue = System.getenv(fallbackKey);
            }

            if (StringUtils.hasText(fallbackValue)) {
                return fallbackValue;
            }
        }

        throw new IllegalStateException("PortOne 설정값이 없습니다: " + String.join(", ", fallbackKeys));
    }
}
