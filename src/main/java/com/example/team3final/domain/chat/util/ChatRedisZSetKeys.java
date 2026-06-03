package com.example.team3final.domain.chat.util;

// 채팅 관련 Redis ZSet 키 상수
public class ChatRedisZSetKeys {

    // 채팅방 READ_ONLY 전환 예약
    // score: 전환 시각 Unix Timestamp / member: chatRoomId
    public static final String ROOM_DEACTIVATE = "chat:room:deactivate";

    private ChatRedisZSetKeys() {} // 인스턴스화 방지
}