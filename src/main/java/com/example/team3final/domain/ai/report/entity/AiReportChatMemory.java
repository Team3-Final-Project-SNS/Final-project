package com.example.team3final.domain.ai.report.entity;

import com.example.team3final.common.entity.BaseTimeEntity;
import com.example.team3final.domain.ai.common.enums.AiChatMemoryRole;
import jakarta.persistence.*;
import lombok.*;

/**
 * 신고 분석 AI의 멀티턴 대화 메모리 엔티티입니다.
 *
 * 관리자 AI가 이전 질의와 답변을 참고해 후속 질문에 답할 수 있도록
 * 최근 대화 내용을 token budget 안에서 다시 구성할 때 사용합니다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(
        name = "ai_report_chat_memory",
        indexes = {
                @Index(name = "idx_ai_report_memory_conversation", columnList = "conversation_id"),
                @Index(name = "idx_ai_report_memory_admin_conversation_created", columnList = "admin_id, conversation_id, created_at")
        }
)
public class AiReportChatMemory extends BaseTimeEntity {

    /**
     * 메모리 row 식별자입니다.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 관리자 AI를 호출한 관리자 ID입니다.
     */
    @Column(nullable = false)
    private Long adminId;

    /**
     * 하나의 관리자 AI 대화 흐름을 묶는 세션 ID입니다.
     */
    @Column(name = "conversation_id", nullable = false, length = 100)
    private String conversationId;

    /**
     * 하나의 관리자 요청과 AI 응답을 연결하는 추적 ID입니다.
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
     * 관리자 질문 또는 AI 답변 본문입니다.
     */
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * 대략적인 토큰 수입니다.
     * 관리자 AI의 이전 대화 맥락을 LLM에 넣을 때 token budget 계산에 사용합니다.
     */
    private Integer tokenCount;
}
