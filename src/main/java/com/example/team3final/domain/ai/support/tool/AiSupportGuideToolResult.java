package com.example.team3final.domain.ai.support.tool;

import com.example.team3final.domain.ai.support.enums.AiSupportCategory;

/**
 * 고객센터 AI의 서비스 안내 Tool 결과입니다.
 *
 * 카테고리별 정책, 사용 방법, 관련 API/화면 정보를 담아
 * LLM이 하드코딩된 추측 대신 내부 안내 데이터에 근거해 답변하도록 합니다.
 *
 * guide에는 rag-docs/support 문서 본문이 들어갑니다.
 * 이 결과는 AiSupportToolResultConverter에서 경계 문자열을 붙여 LLM 컨텍스트로 전달됩니다.
 */
public record AiSupportGuideToolResult(
        AiSupportCategory category,
        String title,
        String guide,
        String relatedApi,
        boolean userActionAvailable
) {
}
