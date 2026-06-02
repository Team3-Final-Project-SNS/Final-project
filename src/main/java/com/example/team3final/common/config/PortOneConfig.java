package com.example.team3final.common.config;

import io.portone.sdk.server.payment.PaymentClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PortOneProperties.class)
public class PortOneConfig {

    @Bean
    public PaymentClient paymentClient(PortOneProperties properties) {
        // PortOne V2 Java SDK의 PaymentClient 빈 등록
        // apiSecret: 서버가 PortOne API 호출 시 사용하는 인증키 (환경변수로 주입)
        // null: baseUrl 기본값 사용 (api.portone.io)
        // storeId: 상점 식별자 (환경변수로 주입)
        return new PaymentClient(
                properties.apiSecret(),
                "https://api.portone.io",  // ← apiBase 기본값 직접 명시
                properties.storeId()
        );
    }
}
