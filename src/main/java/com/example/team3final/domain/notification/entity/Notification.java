package com.example.team3final.domain.notification.entity;

import com.example.team3final.domain.notification.enums.NotificationDomain;
import com.example.team3final.domain.notification.enums.NotificationType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 알림 유형: NotificationType
 * 연관 도메인: NotificationDomain
 * 읽음 여부: isRead (false = 미읽음, true = 읽음)
 */

@Getter
@Entity
@Table(name = "notifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // 수신 유저 ID
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    // 알림 유형
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 30)
    private NotificationType type;

    // 알림 제목
    @Column(name = "title", nullable = false, updatable = false, length = 100)
    private String title;

    // 알림 내용
    @Column(name = "content", nullable = false, updatable = false, length = 500)
    private String  content;

    // 연관 도메인
    @Enumerated(EnumType.STRING)
    @Column(name = "domain", nullable = false, updatable = false, length = 20)
    private NotificationDomain domain;

    // 관련 도메인 ID (클릭 시 화면 이동용)
    @Column(name = "related_id", updatable = false)
    private Long relatedId;

    // 읽음 여부
    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    // 읽은 시각
    @Column(name = "read_at")
    private LocalDateTime readAt;

    // 생성일
    @Column(name = "created_at", nullable = false, updatable = false)
    private  LocalDateTime createdAt;

    @Builder
    private Notification(Long userId, NotificationType type, String title,
                         String content, NotificationDomain domain, Long relatedId) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.content = content;
        this.domain = domain;
        this.relatedId = relatedId;
        this.isRead = false;    // 생성 시 미읽음
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // ==================== 도메인 메서드 ====================

    // 단건 읽음 처리
    public void markAsRead() {
        this.isRead = true;
        this.readAt = LocalDateTime.now();
    }
}
