package com.example.team3final.domain.ai.matching.entity;

import com.example.team3final.common.entity.BaseTimeEntity;
import com.example.team3final.domain.ai.common.enums.AiChatMemoryRole;
import jakarta.persistence.*;
import lombok.*;

/**
 * 매칭 AI 응답 결과를 저장하는 엔티티입니다.
 *
 * 매칭 AI가 사용자 조건을 해석해 생성한 답변과 추천 결과 메타데이터를 저장합니다.
 * 멀티턴 맥락 유지 전용인 AiMatchingChatMemory와 분리하여,
 * 이 엔티티는 응답 결과 기록과 품질 분석에 사용합니다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(
        name = "ai_matching_chat_messages",
        indexes = {
                @Index(name = "idx_ai_matching_chat_conversation", columnList = "conversation_id"),
                @Index(name = "idx_ai_matching_chat_user_created", columnList = "user_id, created_at"),
                @Index(name = "idx_ai_matching_chat_request", columnList = "request_id")
        }
)
public class AiMatchingChatMessage extends BaseTimeEntity {

    /**
     * 메시지 row 식별자입니다.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 매칭 AI 대화 소유 사용자 ID입니다.
     */
    @Column(nullable = false)
    private Long userId;

    /**
     * 매칭 AI 대화를 묶는 conversationId입니다.
     */
    @Column(name = "conversation_id", nullable = false, length = 100)
    private String conversationId;

    /**
     * 하나의 사용자 요청과 AI 응답을 묶는 추적 ID입니다.
     */
    @Column(nullable = false, length = 64)
    private String requestId;

    /**
     * USER 또는 ASSISTANT 메시지인지 구분합니다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiChatMemoryRole role;

    /**
     * 사용자 원문 또는 AI 답변 본문입니다.
     */
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * Rewrite Query Transformer가 검색 조건으로 재작성한 문장입니다.
     *
     * 현재 응답 생성 후에는 주로 품질 분석용으로 남기며,
     * 사용자가 입력한 문장과 실제 검색 문장이 얼마나 달라졌는지 확인할 때 사용합니다.
     */
    @Column(length = 1000)
    private String rewrittenMessage;

    /**
     * 추천된 모집글 ID 목록입니다.
     *
     * JSON 문자열 형태로 저장합니다.
     * 예: [1, 3, 7]
     *
     * 후속 질문에서 "아까 추천한 것 중에서" 같은 흐름을 처리할 때
     * 직전 추천 후보 범위를 복원하는 데 사용합니다.
     */
    @Column(length = 1000)
    private String recommendedPostIds;

    /**
     * fallback 응답 사용 여부입니다.
     *
     * AI 호출 실패, 추천 후보 부족, 검증 실패 등으로 기본 응답을 반환했는지 기록합니다.
     * 추천 품질 분석과 장애 원인 추적용 메타데이터입니다.
     */
    private Boolean fallbackUsed;

    /**
     * 응답 생성에 사용한 모델명입니다.
     *
     * 모델 교체 후 추천 품질을 비교하기 위한 운영 분석용 값입니다.
     */
    @Column(length = 80)
    private String model;

    /**
     * 응답 생성에 사용한 프롬프트 템플릿 ID입니다.
     *
     * ai_prompt_templates.id와 연결해 어떤 프롬프트 파일이 사용됐는지 추적합니다.
     */
    private Long promptTemplateId;

    /**
     * 응답 생성에 사용한 프롬프트 버전입니다.
     *
     * matching-chat-v3, matching-chat-v4처럼 버전별 응답 품질을 비교하기 위한 값입니다.
     */
    @Column(length = 30)
    private String promptVersion;
}
