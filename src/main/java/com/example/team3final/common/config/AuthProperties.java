package com.example.team3final.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "auth") // "auth" 프리픽스로 시작하는 yml 값들을 이 클래스에 자동으로 바인딩
public class AuthProperties {
    private List<String> requiredTermVersions;
}
