package com.example.team3final.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 portone.* 설정을 타입 안전하게 받는 클래스.
 */
@ConfigurationProperties(prefix = "portone")
public record PortOneProperties(
        String apiSecret,
        String storeId,
        String webhookSecret
) {
}
