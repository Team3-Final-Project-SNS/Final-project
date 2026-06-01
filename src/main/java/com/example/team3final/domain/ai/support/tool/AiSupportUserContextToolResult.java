package com.example.team3final.domain.ai.support.tool;

/**
 * 고객센터 AI가 개인 맞춤 안내에 사용할 로그인 사용자 컨텍스트입니다.
 *
 * 보유 포인트나 계정 상태처럼 사용자의 현재 상태가 필요한 질문에서만
 * Tool로 조회해 LLM에 전달합니다.
 */
public record AiSupportUserContextToolResult(
        Long userId,
        String nickname,
        String major,
        String studentNumber,
        int point,
        String accountStatus
) {
}
