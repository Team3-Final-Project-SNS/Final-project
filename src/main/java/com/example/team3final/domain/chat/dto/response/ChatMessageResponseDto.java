package com.example.team3final.domain.chat.dto.response;

import java.time.LocalDateTime;

public record ChatMessageResponseDto(
        Long messageId,
        Long chatRoomId,
        Long senderId,
        String senderNickname,
        String content,
        // 시스템 안내 메시지 여부
        boolean systemMessage,
        boolean isRead,
        LocalDateTime createdAt
) {
}
