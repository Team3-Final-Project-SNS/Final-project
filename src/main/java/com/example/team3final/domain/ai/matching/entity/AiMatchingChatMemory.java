package com.example.team3final.domain.ai.matching.entity;

import com.example.team3final.common.entity.BaseTimeEntity;
import com.example.team3final.domain.ai.common.enums.AiChatMemoryRole;
import jakarta.persistence.*;
import lombok.*;

/**
 * 매칭 AI의 멀티턴 대화 메모리 엔티티입니다.
 *
 * LLM에 이전 대화 맥락을 다시 넣기 위한 짧은 수명 메모리입니다.
 * 상세 추천 결과 분석은 AiMatchingChatMessage에 저장하고,
 * 이 엔티티는 최근 대화 window를 구성하는 데 집중합니다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(
        name = "ai_matching_chat_memory",
        indexes = {
                @Index(name = "idx_ai_matching_memory_conversation", columnList = "conversation_id"),
                @Index(name = "idx_ai_matching_memory_user_conversation_created", columnList = "user_id, conversation_id, created_at")
        }
)
public class AiMatchingChatMemory extends BaseTimeEntity {

    /**
     * 메모리 row 식별자입니다.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 매칭 AI를 사용한 사용자 ID입니다.
     * 사용자별 대화 메모리 삭제와 조회 기준으로 사용합니다.
     */
    @Column(nullable = false)
    private Long userId;

    /**
     * 하나의 매칭 AI 대화 흐름을 묶는 세션 ID입니다.
     * 같은 conversationId 안의 USER/ASSISTANT 메시지를 최근순으로 읽어 맥락을 구성합니다.
     */
    @Column(name = "conversation_id", nullable = false, length = 100)
    private String conversationId;

    /**
     * 하나의 사용자 요청과 그 응답을 묶는 추적 ID입니다.
     * 현재 요청을 메모리 window에서 제외하고, 로그/메트릭과 연결할 때 사용합니다.
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
     * LLM 맥락에 다시 넣을 대화 본문입니다.
     * 너무 긴 내용은 서비스에서 저장 전에 잘라서 보관합니다.
     */
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * 대략적인 토큰 수입니다.
     * 최근 대화 전체를 넣지 않고 token budget 안에서만 LLM에 전달하기 위해 사용합니다.
     */
    private Integer tokenCount;
}
