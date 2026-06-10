package com.example.team3final.common.config;

import com.example.team3final.common.jwt.AdminJwtAuthenticationFilter;
import com.example.team3final.common.jwt.JwtAuthenticationFilter;
import com.example.team3final.common.jwt.JwtProvider;
import com.example.team3final.common.jwt.SuspendedAccountFilter; // ← 추가
import com.example.team3final.domain.admin.security.AdminDetailsService;
import com.example.team3final.domain.user.service.CustomUserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper; // ← 추가
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.Customizer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtProvider jwtProvider;
    private final CustomUserDetailsService userDetailsService;
    private final AdminDetailsService adminDetailsService;
    private final ObjectMapper objectMapper; // ← 추가: SuspendedAccountFilter에서 JSON 응답 작성에 사용

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // CSRF 비활성화
                .cors(Customizer.withDefaults())
                .headers(headers -> headers
                        .frameOptions(frameOptions -> frameOptions.disable()) // H2 콘솔 iframe 허용
                )
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 인증 없이 접근 가능한 엔드포인트
                        // SSE 챗봇(고객센터, 관리자, 매칭)은 응답 중 ASYNC/ERROR 디스패치가 다시 발생할 수 있습니다.
                        // 최초 요청 인증은 그대로 유지하고, 내부 디스패치가 Security에 재차 차단되는 것만 방지합니다.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/api/v1/auth/email/otp",
                                "/api/v1/auth/email/otp/verify",   // OTP 검증
                                "/api/v1/auth/signup",             // 회원가입
                                "/api/v1/auth/login",              // 로그인
                                "/api/v1/auth/refresh",            // 토큰 재발급
                                "/api/v1/universities",            // 대학 목록 (회원가입 페이지에서 사용)
                                "/ws/**",                          // 웹소켓 경로
                                "/h2-console/**",
                                "/api/v1/admin/auth/login",        // Admin 로그인 열어두기
                                "/swagger-ui/**",                  // Swagger UI 정적 리소스 (HTML, CSS, JS)
                                "/swagger-ui.html",                // Swagger UI 진입점
                                "/v3/api-docs/**",                 // OpenAPI 3.0 JSON 명세 경로
                                "/swagger-resources/**"            // Swagger 설정 리소스
                        ).permitAll()

                        // Actuator 헬스체크 허용
                        .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()

                        // 그 외 모든 요청은 인증 필요
                        .anyRequest().authenticated()
                )

                // Admin 필터 먼저 등록
                // /api/v1/admin/** 경로만 처리 (AdminJwtAuthenticationFilter 내부 shouldNotFilter로 제어)
                .addFilterBefore(
                        adminJwtAuthenticationFilter(),
                        UsernamePasswordAuthenticationFilter.class
                )

                // JWT 필터: UsernamePasswordAuthenticationFilter 앞에 삽입
                // → 모든 요청에서 JWT를 먼저 검증한 후 Spring Security가 처리
                .addFilterBefore(
                        jwtAuthenticationFilter(),
                        UsernamePasswordAuthenticationFilter.class
                )

                // SuspendedAccountFilter: JwtAuthenticationFilter 이후에 실행
                // 실행 순서: AdminJwtFilter → JwtAuthenticationFilter → SuspendedAccountFilter
                // 이유: JwtAuthenticationFilter가 먼저 SecurityContext에 인증 정보를 저장해야
                //       SuspendedAccountFilter가 UserDetailsImpl(status 포함)을 꺼낼 수 있음
                .addFilterAfter(
                        suspendedAccountFilter(),
                        JwtAuthenticationFilter.class
                );

        return http.build();
    }

    // JwtAuthenticationFilter 빈 생성
    // @Bean으로 등록하지 않고 직접 생성 — SecurityConfig 내에서만 사용하므로
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtProvider, userDetailsService);
    }

    // SuspendedAccountFilter 빈 생성
    // ObjectMapper를 주입받아 필터 안에서 JSON 에러 응답을 직접 작성
    @Bean
    public SuspendedAccountFilter suspendedAccountFilter() {
        return new SuspendedAccountFilter(objectMapper);
    }

    // BCrypt 암호화 인코더 — 회원가입 시 비밀번호 암호화, 로그인 시 비밀번호 검증에 사용
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // AuthenticationManager — 로그인 시 이메일/비밀번호 검증에 사용
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration
    ) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    // Admin 전용 JWT 필터 빈 등록
    @Bean
    public AdminJwtAuthenticationFilter adminJwtAuthenticationFilter() {
        return new AdminJwtAuthenticationFilter(jwtProvider, adminDetailsService);
    }
}
