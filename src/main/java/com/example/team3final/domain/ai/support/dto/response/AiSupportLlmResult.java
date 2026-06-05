package com.example.team3final.domain.ai.support.dto.response;

import com.example.team3final.domain.ai.support.enums.AiSupportCategory;

/**
 * 고객센터 AI가 반환해야 하는 구조화 응답 스키마입니다.
 *
 * Spring AI가 모델 응답을 이 record로 파싱하고,
 * 서비스는 파싱 결과를 대화 메시지와 API 응답으로 저장/반환합니다.
 *
 * 필드 의미:
 * - answer: 사용자에게 보여줄 최종 자연어 답변
 * - category: 문의 분류. 운영 통계와 프론트 표시, DB 저장에 사용합니다.
 * - summary: 이후 멀티턴 대화나 관리자 검토에 쓸 짧은 요약
 * - actionRequired: 1:1 문의, 관리자 확인처럼 추가 조치가 필요한지 여부
 */
public record AiSupportLlmResult(
        String answer,
        AiSupportCategory category,
        String summary,
        Boolean actionRequired
) {
}
