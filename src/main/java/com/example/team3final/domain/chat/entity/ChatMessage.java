package com.example.team3final.domain.chat.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ChatRoom과 N:1 관계
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false, updatable = false)
    private ChatRoom chatRoom;

    @Column(name = "sender_id", nullable = false, updatable = false)
    private Long senderId;

    @Column(nullable = false)
    private String content;                              // 메세지 내용

    @Column(name = "is_read", nullable = false)
    private boolean isRead;                              // 읽음 여부

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;                     // 생성일

    @Builder
    private ChatMessage(ChatRoom chatRoom, Long senderId, String content) {
        this.chatRoom = chatRoom;
        this.senderId = senderId;
        this.content = content;
        this.isRead = false;
    }

    // Entity가 처음 저장되기 직전에 생성일을 자동으로 기록
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // 읽음 처리 - 메시지 목록 조회 시
    public void markAsRead() {
        this.isRead = true;
    }
}
