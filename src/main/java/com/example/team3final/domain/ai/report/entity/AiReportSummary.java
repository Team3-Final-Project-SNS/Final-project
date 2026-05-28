package com.example.team3final.domain.ai.report.entity;


import com.example.team3final.common.entity.BaseTimeEntity;
import com.example.team3final.domain.ai.report.enums.AiReportDecisionSuggestion;
import com.example.team3final.domain.ai.report.enums.AiReportRiskLevel;
import com.example.team3final.domain.report.enums.ReportReason;
import jakarta.persistence.*;
import lombok.*;


/**
 * 신고 AI 분석 결과 엔티티입니다.
 *
 * 관리자가 신고를 처리하기 전에 AI가 신고 내용을 요약하고,
 * 위험도와 처리 방향을 제안한 결과를 저장합니다.
 *
 * AI의 판단은 최종 처리 결과가 아니라 관리자 판단을 돕는 참고 정보이며,
 * 실제 신고 채택/기각 처리는 기존 Report 도메인의 관리자 처리 흐름을 따릅니다.
 *
 * 발제 요구사항의 "LLM 응답을 정의된 스키마로 파싱하여 DB에 저장"을
 * 신고 요약 AI 도메인에서 충족하기 위한 엔티티입니다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(
        name = "ai_report_summaries",
        indexes = {
                @Index(name = "idx_ai_report_summary_report", columnList = "report_id"),
                @Index(name = "idx_ai_report_summary_request", columnList = "request_id")
        }
)
public class AiReportSummary extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 분석 대상 신고 ID입니다.
     *
     * 기존 Report 엔티티의 id와 연결됩니다.
     */
    @Column(name = "report_id", nullable = false)
    private Long reportId;

    /**
     * AI 분석을 요청한 관리자 ID입니다.
     *
     * 자동 배치 분석이거나 시스템 호출인 경우 null일 수 있습니다.
     */
    private Long adminId;

    /**
     * 하나의 AI 요청을 추적하기 위한 요청 ID입니다.
     *
     * AiCallMetric의 requestId와 연결하여
     * 응답 지연, 에러 상태, 토큰 사용량을 함께 추적할 수 있습니다.
     * UUID 때문에 64로 일단 설정.
     *  더 정확히는 AI 요청 단위 추적용 식별자.
     */
    @Column(nullable = false, length = 64)
    private String requestId;

    /**
     * 기존 신고 사유입니다.
     *
     * Report.reason 값을 함께 저장하여
     * AI 분석 결과와 원본 신고 사유를 비교할 수 있게 합니다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportReason reportReason;

    /**
     * AI가 제안한 신고 처리 방향입니다.
     *
     * 최종 처리 결과가 아니라 관리자 판단 보조용입니다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AiReportDecisionSuggestion decisionSuggestion;

    /**
     * AI가 판단한 신고 위험도입니다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiReportRiskLevel riskLevel;

    /**
     * AI가 생성한 신고 요약입니다.
     *
     * 신고 상세 내용, 신고 사유, 판단 근거를 짧게 요약합니다.
     */
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    /**
     * AI가 판단한 주요 근거.
     *
     * 예:
     * - 욕설 표현 포함
     * - 사기 의심 정황 부족
     * - 신고 상세 내용이 불충분함
     */
    @Column(length = 500)
    private String evidence;

    /**
     * AI가 처리 방향을 제안한 이유.
     */
    @Column(length = 1000)
    private String recommendationReason;

    /**
     * AI 판단 신뢰도.
     *
     * 0부터 100까지의 정수로 저장.
     */
    private Integer confidenceScore;

    /**
     * 추가 관리자 확인이 필요한지 여부입.
     *
     * 신고 내용이 불충분하거나 AI 판단 신뢰도가 낮은 경우 true로 저장.
     */
    @Column(nullable = false)
    private boolean needsAdminReview;

    /**
     * fallback 응답 사용 여부입니다.
     *
     * AI 호출 실패, 프롬프트 로드 실패, Tool 조회 실패로
     * 기본 분석 결과를 저장한 경우 true.
     */
    @Column(nullable = false)
    private boolean fallbackUsed;

    /**
     * 응답 생성에 사용한 모델명.
     */
    @Column(nullable = false, length = 80)
    private String model;

    /**
     * 응답 생성에 사용한 프롬프트 템플릿 ID.
     */
    private Long promptTemplateId;

    /**
     * 응답 생성에 사용한 프롬프트 버전.
     */
    @Column(length = 30)
    private String promptVersion;
}