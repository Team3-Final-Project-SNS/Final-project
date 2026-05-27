package com.example.team3final.domain.notification.dto.response;

// 미확인 알림 카운트 조회
public record GetUnreadCountResponseDto(
        long unreadCount    // 미확인 알림 수
) {
    public static GetUnreadCountResponseDto of(long unreadCount) {
        return new GetUnreadCountResponseDto(unreadCount);
    }
}
