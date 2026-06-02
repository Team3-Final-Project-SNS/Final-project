package com.example.team3final.domain.ai.support.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 고객센터 AI가 분류한 문의 카테고리입니다.
 *
 * AI 응답을 구조화하여 저장할 때 사용하며,
 * 추후 고객센터 대화 이력 조회, 관리자 검토, 통계 분석에 활용할 수 있습니다.
 */
@Getter
@RequiredArgsConstructor
public enum AiSupportCategory {

    // 매칭 관련 문의
    MATCH,

    // 게시글 관련 문의
    POST,

    // 포인트 관련 문의
    POINT,

    // 채팅, 메시지, 알림 문의
    CHAT,

    // 신고 관련 문의
    REPORT,

    // 회원가입, 인증 관련 문의
    ACCOUNT,

    // 노쇼 관련 문의
    MEET,

    // 특정 도메인으로 분류하기 어려운 문의
    GENERAL
}
