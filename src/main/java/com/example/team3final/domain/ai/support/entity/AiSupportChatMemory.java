package com.example.team3final.domain.ai.support.entity;

import com.example.team3final.common.entity.BaseTimeEntity;
import com.example.team3final.domain.ai.common.enums.AiChatMemoryRole;
import jakarta.persistence.*;
import lombok.*;

/**
 * 고객센터 AI의 멀티턴 대화 메모리 엔티티입니다.
 *
 * 고객센터 답변 생성 시 최근 대화 맥락을 LLM에 전달하기 위한 저장소입니다.
 * 정책 답변의 상세 메타데이터는 AiSupportChatMessage에 저장하고,
 * 이 엔티티는 token budget 기반 대화 window 구성에 사용합니다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(
        name = "ai_support_chat_memory",
        indexes = {
                @Index(name = "idx_ai_support_memory_conversation", columnList = "conversation_id"),
                @Index(name = "idx_ai_support_memory_user_conversation_created", columnList = "user_id, conversation_id, created_at")
        }
)
public class AiSupportChatMemory extends BaseTimeEntity {

    /**
     * 메모리 row 식별자입니다.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 고객센터 AI를 사용한 사용자 ID입니다.
     */
    @Column(nullable = false)
    private Long userId;

    /**
     * 하나의 고객센터 대화 흐름을 묶는 세션 ID입니다.
     */
    @Column(name = "conversation_id", nullable = false, length = 100)
    private String conversationId;

    /**
     * 하나의 사용자 요청과 AI 응답을 연결하는 추적 ID입니다.
     */
    @Column(nullable = false, length = 64)
    private String requestId;

    /**
     * USER 메시지인지 ASSISTANT 메시지인지 구분합니다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiChatMemoryRole role;

    /**
     * LLM에 이전 맥락으로 전달할 대화 본문입니다.
     */
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * 대략적인 토큰 수입니다.
     * 고객센터 AI가 최근 대화를 너무 많이 넣지 않도록 window 크기를 제한하는 데 사용합니다.
     */
    private Integer tokenCount;
}
