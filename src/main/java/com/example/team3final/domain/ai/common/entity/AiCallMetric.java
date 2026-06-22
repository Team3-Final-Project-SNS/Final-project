package com.example.team3final.domain.ai.common.entity;

import com.example.team3final.common.entity.BaseTimeEntity;
import com.example.team3final.domain.ai.common.enums.AiCallStatus;
import com.example.team3final.domain.ai.common.enums.AiErrorType;
import com.example.team3final.domain.ai.common.enums.AiFeature;
import jakarta.persistence.*;
import lombok.*;


/**
 * AI 호출 메트릭을 저장하는 엔티티입니다.
 *
 * 기능별 토큰 사용량, 응답 시간, 성공/실패 상태를 기록하여
 * 비용 추적과 장애 분석에 사용합니다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AiCallMetric extends BaseTimeEntity {

    /**
     * AI 호출 메트릭 row 식별자입니다.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 하나의 AI 요청을 추적하기 위한 ID입니다.
     * 로그와 메트릭을 연결할 때 사용합니다.
     */
    @Column(nullable = false, length = 64)
    private String requestId;

    /**
     * AI를 호출한 사용자 ID입니다.
     * 시스템 호출이거나 비로그인 요청이면 null일 수 있습니다.
     */
    private Long userId;

    /**
     * 호출된 AI 기능입니다.
     * 매칭 AI, 고객센터 AI, 관리자 AI 등 기능별 비용과 장애율을 나눠 보기 위한 기준입니다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AiFeature feature;

    /**
     * 호출에 사용한 모델명입니다.
     * 모델별 비용, 응답 시간, 실패율을 비교할 때 사용합니다.
     */
    @Column(nullable = false, length = 80)
    private String model;

    /**
     * 프롬프트 입력 토큰 수입니다.
     * 모델 호출 비용 계산과 긴 프롬프트 탐지에 사용합니다.
     */
    private Integer promptTokens;

    /**
     * AI 응답 생성 토큰 수입니다.
     * 응답 길이와 비용 추적에 사용합니다.
     */
    private Integer completionTokens;


    /**
     * 사용된 프롬프트 템플릿 ID입니다.
     * 프롬프트 버전별 성능/비용 분석에 사용합니다.
     */
    private Long promptTemplateId;

    /**
     * 사용된 프롬프트 버전입니다.
     * 예: v1, v2, v3
     */
    @Column(length = 30)
    private String promptVersion;

    /**
     * 전체 토큰 수입니다.
     * promptTokens + completionTokens 기준이며, AI 사용량 급증 알림의 기초 데이터입니다.
     */
    private Integer totalTokens;

    /**
     * AI 호출 응답 시간입니다. millisecond 단위입니다.
     * 외부 AI API 지연이나 프롬프트 변경 후 성능 저하를 확인하는 데 사용합니다.
     */
    private Long latencyMs;

    /**
     * AI 호출 처리 상태입니다.
     * 성공, 실패, fallback 여부를 기능별로 집계하기 위한 기준입니다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AiCallStatus status;

    /**
     * 실패한 경우의 오류 유형입니다.
     * 성공한 경우 null일 수 있습니다.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private AiErrorType errorType;

    /**
     * 실패 상세 메시지입니다.
     * 민감정보, JWT, 프롬프트 전문은 저장하지 않습니다.
     * 장애 원인 파악에 필요한 짧은 에러 설명만 저장합니다.
     */
    @Column(length = 500)
    private String errorMessage;
}
