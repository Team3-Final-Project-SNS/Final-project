package com.example.team3final.domain.ai.report.entity;

import com.example.team3final.common.entity.BaseTimeEntity;
import com.example.team3final.domain.ai.report.enums.AiAdminAnswerSource;
import com.example.team3final.domain.ai.report.enums.AiAdminCategory;
import com.example.team3final.domain.ai.report.enums.AiReportDecisionSuggestion;
import com.example.team3final.domain.ai.report.enums.AiReportRiskLevel;
import com.example.team3final.domain.report.enums.ReportReason;
import jakarta.persistence.*;
import lombok.*;

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
     */
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String requestMessage;

    /**
     * 관리자 화면에 보여준 AI 답변입니다.
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
     */
    @Column(length = 1000)
    private String evidence;

    /**
     * AI가 관리자에게 권장한 후속 확인 또는 처리 방향입니다.
     */
    @Column(length = 1000)
    private String recommendation;

    /**
     * 신고 분석 결과인 경우 원본 신고 사유를 저장합니다.
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
     */
    private Integer confidenceScore;

    /**
     * 관리자 추가 확인이 필요한지 여부입니다.
     */
    @Column(nullable = false)
    private boolean needsAdminReview;

    /**
     * 답변 생성에 사용한 데이터 출처입니다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AiAdminAnswerSource answerSource;

    @Column(nullable = false)
    private boolean toolUsed;

    @Column(nullable = false)
    private boolean ragUsed;

    @Column(nullable = false)
    private boolean retrievalFallbackUsed;

    @Column(nullable = false)
    private boolean fallbackUsed;

    @Column(nullable = false, length = 80)
    private String model;

    private Long promptTemplateId;

    @Column(length = 30)
    private String promptVersion;
}
