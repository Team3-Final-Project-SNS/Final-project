package com.example.team3final.domain.ai.matching.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;

/**
 * 매칭 AI 요청 1회에 바인딩되는 Tool 어댑터입니다.
 *
 * LLM에는 현재 로그인 사용자의 이메일을 직접 입력받는 Tool을 노출하지 않고,
 * 서버가 인증 객체에서 얻은 email을 고정해서 모집글 조회와 신청 가능 여부 검증을 수행합니다.
 */
public class AiMatchingSessionTool {

    private final AiMatchingTool aiMatchingTool;
    private final String email;

    public AiMatchingSessionTool(AiMatchingTool aiMatchingTool, String email) {
        this.aiMatchingTool = aiMatchingTool;
        this.email = email;
    }

    /**
     * 현재 로그인 사용자의 학교, 포인트, 신청 이력을 기준으로 모집 중인 식사팟 후보를 조회합니다.
     */
    @Tool(
            description = "현재 로그인 사용자의 자연어 식사 조건에 맞는 모집 중인 식사팟 후보를 조회합니다.",
            resultConverter = AiMatchingToolResultConverter.class
    )
    public List<AiMatchingPostToolResult> searchRecruitingMealPosts(
            @ToolParam(description = "Rewrite Query Transformer로 정리한 식사 조건", required = true)
            String condition
    ) {
        return aiMatchingTool.searchRecruitingMealPostsForAi(email, condition);
    }

    /**
     * 현재 로그인 사용자가 특정 게시글에 신청 가능한지 최종 검증합니다.
     */
    @Tool(
            description = "현재 로그인 사용자가 특정 게시글에 신청 가능한지 최종 검증합니다.",
            resultConverter = AiMatchingToolResultConverter.class
    )
    public AiMatchingPostToolResult checkApplicationAvailability(
            @ToolParam(description = "신청 가능 여부를 확인할 게시글 ID", required = true)
            Long postId
    ) {
        return aiMatchingTool.checkApplicationAvailability(email, postId);
    }
}
