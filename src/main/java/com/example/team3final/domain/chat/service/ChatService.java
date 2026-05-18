package com.example.team3final.domain.chat.service;

public interface ChatService {

    // 채팅방 생성 - 매칭 확정 시 내부 호출
    void createChatRoom(Long matchId);
}
