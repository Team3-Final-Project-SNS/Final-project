package com.example.team3final.domain.chat.dto.response;

import java.time.LocalDateTime;

public record ChatMessageResponseDto(
        Long messageId,        // 메시지 ID
        Long chatRoomId,       // 채팅방 ID
        Long senderId,         // 발신자 ID
        String senderNickname, // 발신자 닉네임
        String content,        // 메시지 내용
        boolean isRead,        // 읽음 여부
        LocalDateTime createdAt // 생성일
) {
}
