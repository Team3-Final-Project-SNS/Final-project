package com.example.team3final.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry
                // /** -> 모든 API 경로에 CORS 설정 허용
                .addMapping("/api/**")

                // 허용할 프론트엔드 출처
                .allowedOrigins("http://localhost:5173")

                // 허용할 HTTP 메서드
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")

                // 허용할 헤더 (userId 임시 헤더 포함)
                .allowedHeaders("*")

                // 인증 정보 (쿠키, Authorization 헤더) 포함 허용
                // JWT 붙을 때 필요하므로 미리 설정
                .allowCredentials(true)

                // Preflight 요청 캐시 시간 (초) -> 매 요청마다 Preflight 안 보내도 됨
                .maxAge(3600);
    }
}
