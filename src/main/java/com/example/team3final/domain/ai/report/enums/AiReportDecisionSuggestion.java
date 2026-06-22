package com.example.team3final.domain.ai.report.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 신고 AI가 제안하는 관리자 처리 방향.
 *
 * AI는 신고를 최종 처리하지 않고, 관리자 판단을 돕기 위한
 * 참고 의견만 제공.
 */
@Getter
@RequiredArgsConstructor
public enum AiReportDecisionSuggestion {

    ACCEPT("신고 채택 권고"),
    REJECT("신고 기각 권고"),
    NEEDS_REVIEW("관리자 추가 검토 권고");

    private final String description;
}
