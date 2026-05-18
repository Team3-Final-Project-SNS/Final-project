package com.example.team3final.domain.chat.entity;

import com.example.team3final.common.entity.BaseEntity;
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
public class ChatRoom extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id", nullable = false, unique = true)
    private Long matchId;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;                       // 활성 여부

    @Column(name = "deactiveated_at")
    private LocalDateTime deactivatedAt;            // 비활성화 시각

    @Column(name = "author_left", nullable = false)
    private boolean authorLeft;                     // 등록자 나가기 여부

    @Column(name = "applicant_left", nullable = false)
    private boolean applicantLeft;                  // 신청자 나가기 여부

    @Builder
    private ChatRoom



}
