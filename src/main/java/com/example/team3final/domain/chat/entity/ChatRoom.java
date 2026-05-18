package com.example.team3final.domain.chat.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_room")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id", nullable = false, unique = true)
    private Long matchId;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;                       // 활성 여부

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;            // 비활성화 시각

    @Column(name = "author_left", nullable = false)
    private boolean authorLeft;                     // 등록자 나가기 여부

    @Column(name = "applicant_left", nullable = false)
    private boolean applicantLeft;                  // 신청자 나가기 여부

    @Builder
    private ChatRoom(Long matchId) {
        this.matchId = matchId;
        this.isActive = true;       // 생성 시 활성화
        this.authorLeft = false;    // 생성 시 등록자 나가지 않은 상태
        this.applicantLeft = false; // 생성 시 신청자 나가지 않은 상태
    }

    // 채팅방 비활성화 - 완료/취소/노쇼 시
    public void deactivate() {
        this.isActive = false;
        this.deactivatedAt = LocalDateTime.now();
    }

    // 등록자 나가기
    public void authorLeave() {
        this.authorLeft = true;
    }

    // 신청자 나가기
    public void applicantLeave() {
        this.applicantLeft = true;
    }

    // 등록자 재입장
    public void authorReEnter() {
        this.authorLeft = false;
    }

    // 신청자 재입장
    public void applicantReEnter() {
        this.applicantLeft = false;
    }
}

