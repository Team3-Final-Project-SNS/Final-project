package com.example.team3final.common.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtProvider {

    @Value("${jwt.secret-key}")
    private String secretKeyString;

    @Value("${jwt.access-token-validity-time}")
    private long accessTokenValidityTime;

    @Value("${jwt.refresh-token-validity-time}")
    private long refreshTokenValidityTime;

    @Value("${jwt.signup-token-validity-time}")
    private long signupTokenValidityTime;

    private SecretKey secretKey;

    @PostConstruct
    protected  void init() {
        this.secretKey = Keys.hmacShaKeyFor(secretKeyString.getBytes(StandardCharsets.UTF_8));
    }

    // ===== Access Token 생성 =====
    // subject: 이메일 (토큰 식별자로 사용)
    public String generateAccessToken(String email) {
        return buildToken(email, accessTokenValidityTime, "ACCESS");
    }

    // ===== Refresh Token 생성 =====
    public String generateRefreshToken(String email) {
        return buildToken(email, refreshTokenValidityTime, "REFRESH");
    }

    // ===== Signup Token 생성 =====
    // subject: 이메일, 회원가입 진행 중에만 사용하는 임시 토큰
    public String generateSignupToken(String email) {
        return buildToken(email, signupTokenValidityTime, "SIGNUP");
    }

    // ===== 공통 토큰 빌드 로직 =====
    private String buildToken(String subject, long validityTime, String tokenType) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + validityTime);

        return Jwts.builder()
                .subject(subject)                  // 토큰 주인 (이메일)
                .claim("type", tokenType)       // 토큰 타입 구분 (ACCESS/REFRESH/SIGNUP)
                .issuedAt(now)                     // 발급 시각
                .expiration(expiry)                // 만료 시각
                .signWith(secretKey)               // 서명 (위조 방지)
                .compact();                        // 문자열로 직렬화
    }

    // ===== 토큰에서 이메일(subject) 추출 =====
    public String getEmailFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    // ===== 토큰 타입 추출 =====
    // ACCESS / REFRESH / SIGNUP 중 어떤 토큰인지 확인
    public String getTokenType(String token) {
        return (String) parseClaims(token).get("type");
    }

    // ===== 토큰 유효성 검증 =====
    // 유효하면 true, 만료/위조/형식오류면 false
    public boolean validateToken(String token) {
        try {
            parseClaims(token); // 파싱 성공 = 유효
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("만료된 JWT 토큰: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("지원하지 않는 JWT 토큰: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("잘못된 형식의 JWT 토큰: {}", e.getMessage());
        } catch (SecurityException e) {
            log.warn("JWT 서명 오류: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT 토큰이 비어있음: {}", e.getMessage());
        }
        return false;
    }

    // ===== Claims(페이로드) 파싱 =====
    // 서명 검증 + 만료시간 검증까지 자동으로 처리됨
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)   // 서명 검증에 사용할 키 지정
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
