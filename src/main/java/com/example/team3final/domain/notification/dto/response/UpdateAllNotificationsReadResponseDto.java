package com.example.team3final.domain.notification.dto.response;

import com.example.team3final.domain.notification.entity.Notification;

// 알림 전체 읽음 처리
public record UpdateAllNotificationsReadResponseDto(
        int updatedCount    // 읽음 처리된 알림 수
) {
    public static UpdateAllNotificationsReadResponseDto of(int updatedCount) {
        return new UpdateAllNotificationsReadResponseDto(updatedCount);
    }
}
