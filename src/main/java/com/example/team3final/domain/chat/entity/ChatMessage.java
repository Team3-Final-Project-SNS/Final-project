package com.example.team3final.domain.chat.entity;

import com.example.team3final.common.entity.BaseTimeEntity;
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
public class ChatMessage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // 소속 채팅방 ID
    @Column(name = "chat_room_id", nullable = false, updatable = false)
    private Long chatRoomId;

    // 발신자 유저 ID
    @Column(name = "sender_id", nullable = false, updatable = false)
    private Long senderId;

    // 메시지 내용
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    // 읽음 여부
    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @Builder
    private ChatMessage(Long chatRoomId, Long senderId, String content) {
        this.chatRoomId = chatRoomId;
        this.senderId = senderId;
        this.content = content;
        this.isRead = false;
    }

    // ==================== 도메인 메서드 ====================

    // 메시지 읽음 처리
    public void markAsRead() {
        this.isRead = true;
    }
}