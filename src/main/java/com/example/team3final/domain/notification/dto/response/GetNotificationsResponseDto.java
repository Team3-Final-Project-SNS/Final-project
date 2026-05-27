package com.example.team3final.domain.notification.dto.response;

import com.example.team3final.domain.notification.entity.Notification;
import com.example.team3final.domain.notification.enums.NotificationType;
import com.example.team3final.domain.notification.enums.RelatedDomain;
import java.time.LocalDateTime;

// 알림 목록 조회
public record GetNotificationsResponseDto(
        Long notificationId,        // 알림 ID
        NotificationType type,      // 알림 유형
        String title,               // 알림 제목
        String content,             // 알림 내용
        RelatedDomain domain,       // 연관 도메인
        Long relatedId,             // 연관 도메인 ID (클릭 시 화면 이동용)
        boolean isRead,             // 읽음 여부
        LocalDateTime readAt,       // 읽은 시각
        LocalDateTime createdAt     // 생성일
) {
    public static GetNotificationsResponseDto from(Notification notification) {
        return new GetNotificationsResponseDto(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getContent(),
                notification.getRelatedDomain(),
                notification.getRelatedId(),
                notification.isRead(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }
}

