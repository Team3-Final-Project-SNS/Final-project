package com.example.team3final.domain.chat.dto.response;

import java.time.LocalDateTime;

public record ChatRoomResponseDto(
        Long chatRoomId,           // 채팅방 ID
        Long matchId,              // 매칭 ID
        Long opponentId,           // 상대방 유저 ID
        String opponentNickname,   // 상대방 닉네임
        String lastMessage,        // 마지막 메시지 내용
        LocalDateTime lastMessageAt, // 마지막 메시지 시각
        long unreadCount,          // 안읽은 메시지 수
        boolean isActive,          // 활성 여부
        LocalDateTime createdAt    // 채팅방 생성일
) {
}
