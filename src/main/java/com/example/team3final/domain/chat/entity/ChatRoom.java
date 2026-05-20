package com.example.team3final.domain.chat.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;


import java.time.LocalDateTime;

@Entity
@Table(name = "chat_room")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id", nullable = false, unique = true, updatable = false)
    private Long matchId;

    // TODO: Match 도메인 구현 완료 후 아래 방식으로 교체 예정
    // Match matchId로 조회 → match.getPostId()로 Post 조회 → post.getAuthorId() 반환
    // ChatServiceImpl.createChatRoom() 파라미터에서 authorId 제거 예정
    @Column(name = "author_id", nullable = false, updatable = false)
    private Long authorId;      // 등록자 ID (임시 저장)

    // TODO: Match 도메인 구현 완료 후 아래 방식으로 교체 예정
    // Match matchId로 조회 → match.getApplicantId() 반환
    // ChatServiceImpl.createChatRoom() 파라미터에서 applicantId 제거 예정
    @Column(name = "applicant_id", nullable = false, updatable = false)
    private Long applicantId;   // 신청자 ID (임시 저장)

    @Column(name = "is_active", nullable = false)
    private boolean isActive;                       // 활성 여부

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;                // 생성일

    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;            // 비활성화 시각

    @Column(name = "author_left", nullable = false)
    private boolean authorLeft;                     // 등록자 나가기 여부

    @Column(name = "applicant_left", nullable = false)
    private boolean applicantLeft;                  // 신청자 나가기 여부

    @Builder
    private ChatRoom(Long matchId, Long authorId, Long applicantId) {
        this.matchId = matchId;
        this.authorId = authorId;       // TODO: Match 도메인 구현 완료 후 파라미터 삭제 예정
        this.applicantId = applicantId; // TODO: Match 도메인 구현 완료 후 파라미터 삭제 예정
        this.isActive = true;       // 생성 시 활성화
        this.authorLeft = false;    // 생성 시 등록자 나가지 않은 상태
        this.applicantLeft = false; // 생성 시 신청자 나가지 않은 상태
    }

    // Entity가 처음 저장되기 직전에 생성일을 자동으로 기록
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // 채팅방 즉시 비활성화 - (취소/노쇼 시)
    public void deactivateNow() {
        this.isActive = false;
        this.deactivatedAt = LocalDateTime.now();
    }

    // 2시간 후 비활성화 예약 (만남 인증 완료 시)
    public void scheduleDeactivation() {
        this.deactivatedAt = LocalDateTime.now().plusHours(2);
    }

    // 스케줄러에서 호출 - deactivatedAt 지난 채팅방 비활성화
    public void deactivate() {
        this.isActive = false;
    }

    // 등록자 나가기
    public void authorLeave() {
        this.authorLeft = true;
    }

    // 신청자 나가기
    public void applicantLeave() {
        this.applicantLeft = true;
    }
}


