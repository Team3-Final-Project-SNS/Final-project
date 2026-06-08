package com.example.team3final.common.config;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

// WebSocket 연결 시 Principal을 email로 설정
// convertAndSendToUser(email, ...) 가 동작하려면 Principal.getName() = email 이어야 함
@Component
public class ChatHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(
            ServerHttpRequest request,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        // ChatHandshakeInterceptor에서 저장한 email 꺼내기
        String email = (String) attributes.get("email");
        if (email == null) {
            return null;
        }
        // email을 Principal name으로 설정
        return new EmailPrincipal(email);
    }
}