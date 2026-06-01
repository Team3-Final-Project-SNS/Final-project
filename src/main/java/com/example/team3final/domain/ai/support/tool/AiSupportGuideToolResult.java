package com.example.team3final.domain.ai.support.tool;

import com.example.team3final.domain.ai.support.enums.AiSupportCategory;

/**
 * 고객센터 AI의 서비스 안내 Tool 결과입니다.
 *
 * 카테고리별 정책, 사용 방법, 관련 API/화면 정보를 담아
 * LLM이 하드코딩된 추측 대신 내부 안내 데이터에 근거해 답변하도록 합니다.
 */
public record AiSupportGuideToolResult(
        AiSupportCategory category,
        String title,
        String guide,
        String relatedApi,
        boolean userActionAvailable
) {
}
