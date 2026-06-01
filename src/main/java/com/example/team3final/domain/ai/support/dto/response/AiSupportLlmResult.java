package com.example.team3final.domain.ai.support.dto.response;

import com.example.team3final.domain.ai.support.enums.AiSupportCategory;

/**
 * 고객센터 AI가 반환해야 하는 구조화 응답 스키마입니다.
 *
 * Spring AI가 모델 응답을 이 record로 파싱하고,
 * 서비스는 파싱 결과를 대화 메시지와 API 응답으로 저장/반환합니다.
 */
public record AiSupportLlmResult(
        String answer,
        AiSupportCategory category,
        String summary,
        Boolean actionRequired
) {
}
