package com.example.team3final.domain.ai.report.entity;

import com.example.team3final.common.entity.BaseTimeEntity;
import com.example.team3final.domain.ai.report.enums.AiAdminAnswerSource;
import com.example.team3final.domain.ai.report.enums.AiAdminCategory;
import com.example.team3final.domain.ai.report.enums.AiReportDecisionSuggestion;
import com.example.team3final.domain.ai.report.enums.AiReportRiskLevel;
import com.example.team3final.domain.report.enums.ReportReason;
import jakarta.persistence.*;
import lombok.*;

/**
 * 관리자 AI가 생성한 업무 답변과 판단 결과를 저장하는 엔티티입니다.
 *
 * 신고/이의제기 분석, 결제 요약, 대시보드 요약처럼 관리자 화면에서 사용한
 * AI 응답 원문과 근거, 추천 조치, 사용한 도구/RAG 여부를 함께 기록합니다.
 * 일부 컬럼은 현재 화면에 바로 노출되지 않더라도 프롬프트 버전별 품질 분석과
 * 운영 감사 로그로 활용하기 위해 보관합니다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(
        name = "ai_admin_results",
        indexes = {
                @Index(name = "idx_ai_admin_result_request", columnList = "request_id"),
                @Index(name = "idx_ai_admin_result_conversation", columnList = "conversation_id"),
                @Index(name = "idx_ai_admin_result_category", columnList = "category"),
                @Index(name = "idx_ai_admin_result_target", columnList = "target_type, target_id")
        }
)
public class AiAdminResult extends BaseTimeEntity {

    /**
     * 관리자 AI 결과 row 식별자입니다.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * AI 요청 단위 추적 ID입니다.
     * AiCallMetric.requestId와 연결하여 토큰 사용량, 응답 시간, 상태를 함께 추적합니다.
     */
    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    /**
     * 관리자 AI 대화 세션 ID입니다.
     * 같은 conversationId 안에서 이어지는 후속 질문과 답변을 묶습니다.
     */
    @Column(name = "conversation_id", nullable = false, length = 100)
    private String conversationId;

    /**
     * AI를 호출한 관리자 ID입니다.
     */
    @Column(nullable = false)
    private Long adminId;

    /**
     * 관리자 AI가 처리한 업무 영역입니다.
     * 예: 신고 분석, 이의제기 분석, 결제 요약, 운영 대시보드 요약.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AiAdminCategory category;

    /**
     * 결과가 연결된 관리자 대상 타입입니다.
     * 예: DASHBOARD, POST, REPORT, INQUIRY, DISPUTE, USER, PAYMENT, FAQ, GENERAL
     */
    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;

    /**
     * 특정 신고, 이의제기, 게시글처럼 단건 대상이 있는 경우 대상 ID를 저장합니다.
     * 대시보드 요약이나 일반 질문처럼 단건 대상이 없으면 null입니다.
     */
    @Column(name = "target_id")
    private Long targetId;

    /**
     * 관리자 요청 원문입니다.
     * 감사 로그와 재분석을 위해 사용자가 입력한 질문을 그대로 저장합니다.
     */
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String requestMessage;

    /**
     * 관리자 화면에 보여준 AI 답변입니다.
     * 운영자가 실제로 확인한 최종 응답 본문입니다.
     */
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    /**
     * 관리자 AI 결과 요약입니다.
     * 목록 조회나 관리자 검토 화면에서 짧게 표시할 수 있습니다.
     */
    @Column(length = 500)
    private String summary;

    /**
     * AI 답변에 사용된 근거 요약입니다.
     * Tool 조회 결과나 RAG 정책 문서에서 답변에 반영된 핵심 근거를 짧게 남깁니다.
     */
    @Column(length = 1000)
    private String evidence;

    /**
     * AI가 관리자에게 권장한 후속 확인 또는 처리 방향입니다.
     * 예: 신고 원문 확인, 결제 실패 사유 확인, 환불 상태 검토.
     */
    @Column(length = 1000)
    private String recommendation;

    /**
     * 신고 분석 결과인 경우 원본 신고 사유를 저장합니다.
     * 신고/이의제기 외 일반 관리자 질의에서는 null일 수 있습니다.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ReportReason reportReason;

    /**
     * 신고/이의제기처럼 관리자 판단이 필요한 결과에서 AI가 제안한 처리 방향입니다.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private AiReportDecisionSuggestion decisionSuggestion;

    /**
     * 신고/이의제기처럼 위험도 판단이 필요한 결과에서 AI가 판단한 위험도입니다.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AiReportRiskLevel riskLevel;

    /**
     * AI 판단 신뢰도입니다.
     * 0~100 범위로 저장하며, 낮은 점수일수록 운영자가 직접 확인해야 합니다.
     */
    private Integer confidenceScore;

    /**
     * 관리자 추가 확인이 필요한지 여부입니다.
     * true이면 AI 답변만으로 자동 처리하지 않고 관리자 검토가 필요하다는 의미입니다.
     */
    @Column(nullable = false)
    private boolean needsAdminReview;

    /**
     * 답변 생성에 사용한 데이터 출처입니다.
     * Tool, RAG, fallback 중 어떤 경로로 답변했는지 구분합니다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AiAdminAnswerSource answerSource;

    /**
     * 관리자 도메인 Tool 조회를 사용했는지 여부입니다.
     * 결제/신고/대시보드 데이터처럼 DB 기반 컨텍스트를 사용했는지 확인할 수 있습니다.
     */
    @Column(nullable = false)
    private boolean toolUsed;

    /**
     * RAG 정책 문서 또는 가이드 검색 결과를 답변에 사용했는지 여부입니다.
     */
    @Column(nullable = false)
    private boolean ragUsed;

    /**
     * RAG 검색 결과가 부족해서 Tool 결과나 기본 안내로 보완했는지 여부입니다.
     */
    @Column(nullable = false)
    private boolean retrievalFallbackUsed;

    /**
     * LLM 호출 실패 또는 파싱 실패 등으로 기본 fallback 답변을 사용했는지 여부입니다.
     */
    @Column(nullable = false)
    private boolean fallbackUsed;

    /**
     * 답변 생성에 사용한 모델명입니다.
     * 모델 변경 전후 관리자 AI 품질을 비교하기 위한 값입니다.
     */
    @Column(nullable = false, length = 80)
    private String model;

    /**
     * 답변 생성에 사용한 프롬프트 템플릿 ID입니다.
     * ai_prompt_templates.id와 연결해 어떤 프롬프트가 사용됐는지 추적합니다.
     */
    private Long promptTemplateId;

    /**
     * 답변 생성에 사용한 프롬프트 버전입니다.
     * 관리자 AI 프롬프트 개선 전후 결과 비교에 사용합니다.
     */
    @Column(length = 30)
    private String promptVersion;
}
