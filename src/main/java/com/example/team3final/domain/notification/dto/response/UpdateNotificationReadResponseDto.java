package com.example.team3final.domain.notification.dto.response;

import java.time.LocalDateTime;

public record UpdateNotificationReadResponseDto(
        Long notificationId,  // 읽음 처리된 알림 ID
        boolean isRead,       // 읽음 상태 (항상 true)
        LocalDateTime readAt  // 읽음 처리 시각
) {}