package com.example.team3final.domain.chat.entity;

import com.example.team3final.domain.chat.enums.ChatMemberRole;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "chat_members",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chat_member_room_user",
                columnNames = {"chat_room_id", "user_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // 소속 채팅방 ID
    @Column(name = "chat_room_id", nullable = false, updatable = false)
    private Long chatRoomId;

    // 참여자 유저 ID
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    // 역할 (HOST: 등록자, GUEST: 신청자)
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private ChatMemberRole role;

    // 입장 시각
    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    @Builder
    private ChatMember(Long chatRoomId, Long userId, ChatMemberRole role) {
        this.chatRoomId = chatRoomId;
        this.userId = userId;
        this.role = role;
    }

    // 엔티티가 처음 저장되기 직전에 입장 시각을 자동으로 기록
    @PrePersist
    protected void onCreate() {
        this.joinedAt = LocalDateTime.now();
    }

    // ==================== 도메인 메서드 ====================

    // 특정 유저인지 확인
    public boolean isOwnedBy(Long targetUserId) {
        return this.userId.equals(targetUserId);
    }

    // HOST 여부 확인
    public boolean isHost() {
        return this.role == ChatMemberRole.HOST;
    }

    // GUEST 여부 확인
    public boolean isGuest() {
        return this.role == ChatMemberRole.GUEST;
    }
}