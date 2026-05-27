package com.example.team3final.domain.ai.matching.tool;


/**
 * 매칭 AI Tool 조회 결과 DTO입니다.
 *
 * AiMatchingTool이 같은 학교의 모집 중인 식사팟 후보를 조회한 뒤,
 * LLM 프롬프트와 응답 DTO 변환에 사용할 정보를 담습니다.
 *
 * 게시글 기본 정보뿐 아니라 신청 가능 여부, 포인트 충족 여부,
 * 신청 불가 사유까지 함께 전달하여 LLM이 잘못된 신청 가능 안내를 하지 않도록 합니다.
 */
public record AiMatchingPostToolResult(
        Long postId,
        String placeName,
        String meetAt,
        int deposit,
        String content,
        boolean applicationAvailable,
        boolean pointAffordable,
        String unavailableReason
) {
}