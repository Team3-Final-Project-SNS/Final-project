package com.example.team3final.domain.chat.entity;

import com.example.team3final.common.entity.BaseTimeEntity;
import com.example.team3final.domain.chat.enums.ChatMemberRole;
import com.example.team3final.domain.chat.enums.ChatMemberStatus;
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
public class ChatMember extends BaseTimeEntity {

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

    // 멤버 상태 (ACTIVE: 정상 / NO_SHOW: 노쇼 제한)
    // 기본값 ACTIVE — 노쇼 판정 시 NO_SHOW로 전환
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ChatMemberStatus status = ChatMemberStatus.ACTIVE;

    // 노쇼 판정 시각 — 이 시각 이후 메시지는 해당 멤버에게 노출 안 됨
    // null = 정상 참여 중 / 값 있음 = 노쇼 제한 적용 중
    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @Builder
    private ChatMember(Long chatRoomId, Long userId, ChatMemberRole role) {
        this.chatRoomId = chatRoomId;
        this.userId = userId;
        this.role = role;
        this.status = ChatMemberStatus.ACTIVE; // 생성 시 항상 ACTIVE
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

    // 노쇼 제한 처리 — 전송 차단 + leftAt 기록
    // leftAt 이후 메시지는 메시지 조회 시 필터링됨
    public void markNoShow() {
        this.status = ChatMemberStatus.NO_SHOW;
        this.leftAt = LocalDateTime.now();
    }

    // 노쇼 상태인지 확인
    public boolean isNoShow() {
        return this.status == ChatMemberStatus.NO_SHOW;
    }

    // 메시지 발신·실시간 수신·알림이 허용되는 현재 참여자인지 확인
    public boolean isActive() {
        return this.status == ChatMemberStatus.ACTIVE;
    }
}