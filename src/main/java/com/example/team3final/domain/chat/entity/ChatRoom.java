package com.example.team3final.domain.chat.entity;

import com.example.team3final.common.entity.BaseTimeEntity;
import com.example.team3final.domain.chat.enums.ChatRoomStatus;
import com.example.team3final.domain.chat.enums.ChatRoomType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Entity
@Table(
        name = "chat_rooms",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_chat_rooms_post_id",
                    columnNames = {"post_id"}
            )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom extends BaseTimeEntity {

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

    // 채팅방 상태 (isActive 대체)
    // 기본값: ACTIVE
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ChatRoomStatus status;

    // 비활성화 예약 시각
    // - 만남 완료 시: 지금 + 2시간 (스케줄러가 이 시각에 READ_ONLY 전환)
    // - 노쇼 확정 시: null (스케줄러 대상 제외 — 영구 READ_ONLY)
    // - 매칭 취소 시: 즉시 비활성화 시각
    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    @Builder
    private ChatRoom(Long postId, ChatRoomType roomType) {
        this.postId = postId;
        // 채팅방 타입 누락 시 잘못된 1:1/그룹 데이터 저장 차단
        this.roomType = Objects.requireNonNull(roomType, "roomType은 필수입니다.");
        this.status = ChatRoomStatus.ACTIVE; // 생성 시 항상 ACTIVE
    }

    // ==================== 도메인 메서드 ====================

    // 즉시 비활성화 - 매칭 취소 시 호출
    // ACTIVE → DEACTIVATED (메시지 조회 불가)
    public void deactivateNow() {
        this.status = ChatRoomStatus.DEACTIVATED;
        this.deactivatedAt = LocalDateTime.now();
    }

    public void reactivate() {
        // 관리자 복구 시 비활성화 상태 해제
        this.status = ChatRoomStatus.ACTIVE;
        this.deactivatedAt = null;
    }

    // 만남 인증 완료 시 호출 - 2시간 후 READ_ONLY 전환 예약
    // ACTIVE 유지, deactivatedAt만 세팅
    // deactivatedAt = 스케줄러가 READ_ONLY로 전환할 시각
    public void scheduleDeactivation() {
        this.deactivatedAt = LocalDateTime.now().plusHours(2);
    }

    // 노쇼 확정 시 즉시 읽기 전용 전환
    // ACTIVE → READ_ONLY (메시지 조회만 가능, 복구 불가)
    // deactivatedAt = null → 스케줄러 처리 대상 제외 (영구 READ_ONLY)
    public void deactivateByNoShow() {
        this.status = ChatRoomStatus.READ_ONLY;
        this.deactivatedAt = null;
    }

    // 스케줄러 전용 - 만남 완료 후 2시간 경과 시 읽기 전용 전환
    // ACTIVE → READ_ONLY (영구, 복구 불가)
    public void deactivateByScheduler() {
        this.status = ChatRoomStatus.READ_ONLY;
    }

    // 상태 확인 헬퍼 메서드
    public boolean isActive() {
        return this.status == ChatRoomStatus.ACTIVE;
    }

    public boolean isReadOnly() {
        return this.status == ChatRoomStatus.READ_ONLY;
    }

    public boolean isDeactivated() {
        return this.status == ChatRoomStatus.DEACTIVATED;
    }
}
