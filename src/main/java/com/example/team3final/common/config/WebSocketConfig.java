package com.example.team3final.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker // STOMP 메시지 브로커 활성화
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final ChatHandshakeInterceptor chatHandshakeInterceptor;
    private final ChatHandshakeHandler chatHandshakeHandler;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 일반 및 사용자별 메시지의 실제 전달 경로
        registry.enableSimpleBroker("/sub", "/queue");

        // 클라이언트 발행 경로
        registry.setApplicationDestinationPrefixes("/pub");

        // 사용자별 구독 경로
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/chat") // 웹소켓 연결 엔드포인트
                .setAllowedOriginPatterns("*") // 임시: 모든 origin 허용
                .addInterceptors(chatHandshakeInterceptor)
                .setHandshakeHandler(chatHandshakeHandler)
                .withSockJS(); // SockJS 폴백 지원
    }
}