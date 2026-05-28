package com.example.team3final.domain.notification.dto.response;

import com.example.team3final.domain.notification.entity.Notification;

import java.time.LocalDateTime;

// 알림 읽음 처리
public record UpdateNotificationReadResponseDto(
        Long notificationId,    // 알림 ID
        boolean isRead,         // 읽음 여부 (항상 true)
        LocalDateTime readAt    // 읽은 시각
) {
    public static UpdateNotificationReadResponseDto from(Notification notification) {
        return new UpdateNotificationReadResponseDto(
                notification.getId(),
                notification.isRead(),
                notification.getReadAt()
        );
    }
}

