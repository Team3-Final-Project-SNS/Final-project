package com.example.team3final.common.config;

import com.example.team3final.common.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtProvider jwtProvider;

    // 웹소켓 연결 전 실행
    // JWT 토큰 검증 후 유효하면 userId를 세션에 저장
    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        // 쿼리 파라미터에서 토큰 추출
        // 연결 URL: ws://localhost:8080/ws/chat?token=Bearer {accessToken}
        String query = request.getURI().getQuery();

        if (!StringUtils.hasText(query)) {
            log.warn("[WebSocket] 토큰 없음 - 연결 거부");
            return false; // false 반환 시 연결 거부
        }

        // 실제 토큰 값 추출
        String token = null;
        for (String param : query.split("&")) {
            if (param.startsWith("token=")) {
                token = param.substring(6); // "token=" 6글자 제거
                if (token.startsWith("Bearer ")) {
                    token = token.substring(7); // "Bearer " 7글자 제거
                }
                break;
            }
        }

        // 토큰 유효성 검증
        if (!StringUtils.hasText(token) || !jwtProvider.validateToken(token)) {
            log.warn("[WebSocket] 유효하지 않은 토큰 - 연결 거부");
            return false;
        }

        // 토큰에서 이메일 추출 후 세션에 저장
        // 이후 메시지 핸들러에서 attributes로 꺼내서 사용
        String email = jwtProvider.getEmailFromToken(token);
        attributes.put("email", email);

        log.info("[WebSocket] 연결 허용 - email: {}", email);
        return true; // true 반환 시 연결 허용
    }


    // 웹소켓 연결 후 실행 (사용 안 함)
    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
    }
}