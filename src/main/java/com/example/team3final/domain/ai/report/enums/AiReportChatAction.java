package com.example.team3final.domain.ai.report.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 신고 AI 챗봇이 관리자 메시지에서 선택할 수 있는 실행 액션입니다.
 *
 * ANALYZE_REPORT는 특정 신고 1건 분석, ANALYZE_DISPUTE는 이의제기 1건 분석,
 * HIGH_RISK_USERS는 고위험 유저 조회,
 * DASHBOARD_SUMMARY는 관리자 콘솔 운영 현황 요약, GENERAL_GUIDE는 일반 관리자 도움말,
 * CLARIFY는 필요한 정보가 부족해
 * 관리자에게 다시 질문해야 하는 상태를 의미합니다.
 */
@Getter
@RequiredArgsConstructor
public enum AiReportChatAction {

    ANALYZE_REPORT("신고 단건 분석"),
    ANALYZE_DISPUTE("이의제기 단건 분석"),
    HIGH_RISK_USERS("고위험 유저 조회"),
    DASHBOARD_SUMMARY("관리자 대시보드 운영 현황 요약"),
    GENERAL_GUIDE("일반 안내"),
    CLARIFY("추가 정보 요청");

    private final String description;
}
