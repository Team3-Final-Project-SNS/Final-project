package com.example.team3final.common.config;

import io.portone.sdk.server.payment.PaymentClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//@Configuration
//@EnableConfigurationProperties(PortOneProperties.class)
public class PortOneConfig {

    @Bean
    public PaymentClient paymentClient(PortOneProperties properties) {
        return new PaymentClient(
                properties.apiSecret(), // V2 API Secret
                null,                   // baseUrl: null 이면 기본(api.portone.io)
                properties.storeId()    // storeId: 시그니처가 다르면 이 인자 제거
        );
    }
}
