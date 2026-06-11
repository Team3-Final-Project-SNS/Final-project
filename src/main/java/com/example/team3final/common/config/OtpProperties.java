package com.example.team3final.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "otp")
@Getter
@Setter
public class OtpProperties {
    private int maxResendCount = 5;
    private long expireSeconds = 300;
    private long resendWindowSeconds = 3600;
    private int maxAttempts = 5;
}
