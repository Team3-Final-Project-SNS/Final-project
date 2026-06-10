package com.example.team3final.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    // JWT 인증 스킴의 이름 (Swagger UI에 표시되는 자물쇠 버튼의 이름)
    private static final String SECURITY_SCHEME_NAME = "BearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                // API 기본 정보 설정 (Swagger UI 상단에 표시됨)
                .info(new Info()
                        .title("한끼팟 API")               // API 문서 제목
                        .description("대학생 식사 매칭 플랫폼 한끼팟 API 명세서") // 설명
                        .version("v1.0"))                  // 버전

                // 전역 보안 요구사항 설정:
                // 이 설정을 추가하면 모든 API에 자물쇠 아이콘이 붙고
                // Swagger UI에서 토큰 입력 후 인증 API를 직접 테스트할 수 있음
                .addSecurityItem(new SecurityRequirement()
                        .addList(SECURITY_SCHEME_NAME))

                // 보안 스킴 컴포넌트 등록:
                // Bearer 방식 + JWT 형식의 Authorization 헤더 인증을 정의
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP) // HTTP 인증 방식
                                        .scheme("bearer")               // Bearer 토큰 방식
                                        .bearerFormat("JWT")));         // 형식은 JWT

    }
}
