package com.example.team3final.domain.ai.support.entity;


import com.example.team3final.common.entity.BaseTimeEntity;
import com.example.team3final.domain.ai.support.enums.AiSupportCategory;
import com.example.team3final.domain.ai.support.enums.AiSupportMessageRole;
import jakarta.persistence.*;
import lombok.*;



/**
 * 고객센터 AI 대화 메시지 엔티티입니다.
 *
 * 고객센터 AI의 멀티턴 대화를 메시지 단위로 저장합니다.
 * 사용자 메시지와 AI 응답을 같은 conversationId로 묶어
 * 이전 대화 맥락을 유지하고, 추후 품질 분석 및 관리자 검토에 활용합니다.
 *
 * 발제 요구사항의 "LLM 응답을 정의된 스키마로 파싱하여 DB에 저장"을
 * 고객센터 AI 도메인에서 충족하기 위한 저장 구조입니다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(
        name = "ai_support_chat_messages",
        indexes = {
                @Index(name = "idx_ai_support_conversation", columnList = "conversation_id"),
                @Index(name = "idx_ai_support_user_created", columnList = "user_id, created_at")
        }
)
public class AiSupportChatMessage extends BaseTimeEntity {

    /**
     * 메시지 row 식별자입니다.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 대화 소유 사용자 ID입니다.
     */
    @Column(nullable = false)
    private Long userId;

    /**
     * 대화 세션 ID입니다.
     *
     * 같은 고객센터 대화 흐름의 사용자 메시지와 AI 응답을 묶습니다.
     */
    @Column(name = "conversation_id", nullable = false, length = 100)
    private String conversationId;

    /**
     * 하나의 AI 요청을 추적하기 위한 요청 ID입니다.
     *
     * 사용자 메시지, AI 응답, AiCallMetric을 같은 requestId로 묶을 수 있습니다.
     */
    @Column(nullable = false, length = 64)
    private String requestId;

    /**
     * 메시지 작성 주체입니다.
     *
     * USER는 사용자가 입력한 메시지,
     * ASSISTANT는 AI가 생성한 응답 메시지를 의미합니다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiSupportMessageRole role;

    /**
     * 메시지 내용입니다.
     *
     * USER인 경우 사용자 질문,
     * ASSISTANT인 경우 AI 답변을 저장합니다.
     */
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * AI가 분류한 고객센터 문의 카테고리입니다.
     *
     * ASSISTANT 메시지에 주로 저장하며,
     * USER 메시지에서는 null일 수 있습니다.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private AiSupportCategory category;

    /**
     * AI 응답 요약입니다.
     *
     * 관리자 검토, 검색, 품질 분석에 사용할 수 있습니다.
     * 현재 대화 맥락 구성은 주로 content를 사용하지만,
     * 향후 관리자 화면에서 문의 목록을 짧게 보여줄 때 활용할 수 있습니다.
     */
    @Column(length = 500)
    private String summary;

    /**
     * 사용자에게 추가 행동이 필요한지 여부입니다.
     *
     * 예:
     * - 관리자 문의 필요
     * - 신고 접수 필요
     * - 포인트 내역 확인 필요
     *
     * USER 메시지에서는 null일 수 있습니다.
     *
     * 정책 답변이 단순 안내인지, 사용자가 추가 행동을 해야 하는 답변인지 구분하기 위한 값입니다.
     */
    private Boolean actionRequired;

    /**
     * fallback 응답 사용 여부입니다.
     *
     * ASSISTANT 메시지에서 AI 호출 실패 또는 Tool 실패로
     * 기본 안내 응답을 반환한 경우 true로 저장합니다.
     *
     * USER 메시지에서는 null일 수 있습니다.
     *
     * 고객센터 AI 장애나 정책 RAG 실패가 얼마나 자주 발생하는지 집계할 때 사용합니다.
     */
    private Boolean fallbackUsed;

    /**
     * 응답 생성에 사용한 모델명입니다.
     *
     * ASSISTANT 메시지에 저장합니다.
     * 모델 교체 후 고객센터 답변 품질을 비교하기 위한 운영 분석용 값입니다.
     */
    @Column(length = 80)
    private String model;

    /**
     * 응답 생성에 사용한 프롬프트 템플릿 ID입니다.
     *
     * ASSISTANT 메시지에서 프롬프트 버전별 품질 분석에 사용할 수 있습니다.
     * ai_prompt_templates.id와 연결됩니다.
     */
    private Long promptTemplateId;

    /**
     * 응답 생성에 사용한 프롬프트 버전입니다.
     *
     * support-chat-v2, support-chat-v3처럼 정책 답변 구조와 SSE 가독성 개선 효과를 비교하는 데 사용합니다.
     */
    @Column(length = 30)
    private String promptVersion;
}
