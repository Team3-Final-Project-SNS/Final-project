package com.example.team3final.domain.ai.common.entity;

import com.example.team3final.common.entity.BaseTimeEntity;
import com.example.team3final.domain.ai.common.enums.AiFeature;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * AI 챗봇 대화 세션을 관리하는 엔티티입니다.
 *
 * 매칭 서비스 AI와 고객센터 AI처럼 멀티턴 대화가 필요한 기능에서
 * 사용자별 conversationId, 마지막 대화 시간, 만료 시간을 관리합니다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AiConversationSession extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 대화 세션 소유 사용자 ID입니다.
     */
    @Column(nullable = false)
    private Long userId;

    /**
     * 어떤 AI 챗봇의 세션인지 구분합니다.
     * 예: MATCHING, SUPPORT
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AiFeature feature;

    /**
     * Spring AI ChatMemory 또는 자체 대화 저장소에서 사용할 대화 ID입니다.
     * 예: userId + ":" + feature
     */
    @Column(nullable = false, unique = true, length = 100)
    private String conversationId;

    /**
     * 최근 대화 시각입니다.
     * 세션 만료 기준으로 사용합니다.
     */
    private LocalDateTime lastMessageAt;

    /**
     * 세션 만료 시각입니다.
     */
    private LocalDateTime expiresAt;

    /**
     * 세션 활성 여부입니다.
     */
    @Column(nullable = false)
    private boolean active;

    /**
     * 대화가 이어질 때 마지막 대화 시각과 만료 시각을 갱신합니다.
     */
    public void touch(LocalDateTime lastMessageAt, LocalDateTime expiresAt) {
        this.lastMessageAt = lastMessageAt;
        this.expiresAt = expiresAt;
    }

    /**
     * 세션을 비활성화합니다.
     */
    public void expire() {
        this.active = false;
    }
}