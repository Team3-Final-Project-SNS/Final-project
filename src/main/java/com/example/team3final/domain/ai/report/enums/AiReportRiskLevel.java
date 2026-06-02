package com.example.team3final.domain.ai.report.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 신고 AI가 판단한 신고 위험도.
 *
 * 신고 상세 내용과 신고 사유를 분석하여
 * 관리자 검토 우선순위를 정하는 데 활용.
 */
@Getter
@RequiredArgsConstructor
public enum AiReportRiskLevel {

    LOW,
    MEDIUM,
    HIGH
}
