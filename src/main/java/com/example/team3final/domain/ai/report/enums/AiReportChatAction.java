package com.example.team3final.domain.ai.report.enums;

/**
 * 신고 AI 챗봇이 관리자 메시지에서 선택할 수 있는 실행 액션입니다.
 *
 * ANALYZE_REPORT는 특정 신고 1건 분석, HIGH_RISK_USERS는 고위험 유저 조회,
 * CLARIFY는 필요한 정보가 부족해 관리자에게 다시 질문해야 하는 상태를 의미합니다.
 */
public enum AiReportChatAction {

    ANALYZE_REPORT,
    HIGH_RISK_USERS,
    CLARIFY
}
