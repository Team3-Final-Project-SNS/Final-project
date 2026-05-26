package com.example.team3final.common.config;

import com.example.team3final.common.jwt.AdminJwtAuthenticationFilter;
import com.example.team3final.common.jwt.JwtAuthenticationFilter;
import com.example.team3final.common.jwt.JwtProvider;
import com.example.team3final.domain.admin.security.AdminDetailsService;
import com.example.team3final.domain.user.service.CustomUserDetailsService;
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

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // CSRF 비활성화
                .headers(headers -> headers
                        .frameOptions(frameOptions -> frameOptions.disable()) // H2 콘솔 iframe 허용
                )
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        //인증없이 접근 가능한 엔드포인트
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/api/v1/auth/email/otp",
                                "/api/v1/auth/email/otp/verify",   // OTP 검증
                                "/api/v1/auth/signup",             // 회원가입
                                "/api/v1/auth/login",              // 로그인
                                "/api/v1/auth/refresh",            // 토큰 재발급
                                "/api/v1/universities",            // 대학 목록 (회원가입 페이지에서 사용)
                                "/ws/**",                           // 웹소켓 경로
                                "/h2-console/**",
                                "/api/v1/admin/auth/login"          // Admin 로그인 열어두기
                        ).permitAll()

                        // Actuator 헬스체크 허용
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                        // 그 외 모든 요청은 인증 필요
                        .anyRequest().authenticated()
                )

                // Admin 필터 먼저 등록
                .addFilterBefore(
                        adminJwtAuthenticationFilter(),
                        UsernamePasswordAuthenticationFilter.class)

                // JWT 필터를 Spring Security의 UsernamePasswordAuthenticationFilter 앞에 삽입
                // → 모든 요청에서 JWT를 먼저 검증한 후 Spring Security가 처리
                .addFilterBefore(
                        jwtAuthenticationFilter(),
                        UsernamePasswordAuthenticationFilter.class
                );
        return http.build();
    }

    // JwtAuthenticationFilter 빈 생성
    // @Bean으로 등록하지 않고 직접 생성 — SecurityConfig 내에서만 사용하므로
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtProvider, userDetailsService);
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

    // Admin 전용 JWT 필터 빈 등록 ← 추가
    @Bean
    public AdminJwtAuthenticationFilter adminJwtAuthenticationFilter() {
        return new AdminJwtAuthenticationFilter(jwtProvider, adminDetailsService);
    }
}