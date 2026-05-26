package com.example.team3final.domain.chat.entity;

import com.example.team3final.domain.chat.enums.ChatRoomType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "chat_rooms")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // 게시글 ID (1:1 및 그룹 채팅방 모두 게시글 기준으로 관리)
    @Column(name = "post_id", nullable = false, updatable = false)
    private Long postId;

    // 채팅방 유형
    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false, length = 20)
    private ChatRoomType roomType;

    // 활성 여부
    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    // 생성일
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 비활성화 시각
    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    @Builder
    private ChatRoom(Long postId, ChatRoomType roomType) {
        this.postId = postId;
        this.roomType = (roomType != null) ? roomType : ChatRoomType.ONE_TO_ONE;
        this.isActive = true;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // ==================== 도메인 메서드 ====================

    // 즉시 비활성화 - 취소/노쇼 시 호출
    public void deactivateNow() {
        this.isActive = false;
        this.deactivatedAt = LocalDateTime.now();
    }

    // 2시간 후 비활성화 예약 - 만남 인증 완료 시 호출
    public void scheduleDeactivation() {
        this.deactivatedAt = LocalDateTime.now().plusHours(2);
    }
}