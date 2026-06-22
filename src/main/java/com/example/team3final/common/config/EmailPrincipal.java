package com.example.team3final.common.config;

import java.security.Principal;

// WebSocket Principal — convertAndSendToUser()가 getName()으로 사용자를 찾음
// email을 Principal name으로 사용
public record EmailPrincipal(String email) implements Principal {

    @Override
    public String getName() {
        return email;
    }
}