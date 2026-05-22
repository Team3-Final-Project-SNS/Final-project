package com.example.team3final.domain.chat.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "chat_messages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // 소속 채팅방 -> Long으로 전환 예정
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false, updatable = false)
    private ChatRoom chatRoom;

    // 발신자 유저 ID
    @Column(name = "sender_id", nullable = false, updatable = false)
    private Long senderId;

    // 메시지 내용
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    // 읽음 여부
    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    // 생성일
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private ChatMessage(ChatRoom chatRoom, Long senderId, String content) {
        this.chatRoom = chatRoom;
        this.senderId = senderId;
        this.content = content;
        this.isRead = false;
    }

    // 엔티티가 처음 저장되기 직전에 생성일을 자동으로 기록
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // ==================== 도메인 메서드 ====================

    // 메시지 읽음 처리
    public void markAsRead() {
        this.isRead = true;
    }
}