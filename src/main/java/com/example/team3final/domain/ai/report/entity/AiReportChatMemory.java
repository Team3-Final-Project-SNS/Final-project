package com.example.team3final.domain.ai.report.entity;

import com.example.team3final.common.entity.BaseTimeEntity;
import com.example.team3final.domain.ai.common.enums.AiChatMemoryRole;
import jakarta.persistence.*;
import lombok.*;

/**
 * 신고 분석 AI의 멀티턴 대화 메모리 엔티티입니다.
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long adminId;

    @Column(name = "conversation_id", nullable = false, length = 100)
    private String conversationId;

    @Column(nullable = false, length = 64)
    private String requestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiChatMemoryRole role;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    private Integer tokenCount;
}
