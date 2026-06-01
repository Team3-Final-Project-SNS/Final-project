package com.example.team3final.domain.ai.support.tool;

import com.example.team3final.domain.ai.support.enums.AiSupportCategory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 고객센터 AI 요청 1회에 바인딩되는 Tool 어댑터입니다.
 *
 * LLM에는 현재 로그인 사용자의 이메일을 직접 입력받는 Tool을 노출하지 않고,
 * 서버가 인증 객체에서 얻은 email을 고정해서 사용자 컨텍스트를 조회합니다.
 */
public class AiSupportSessionTool {

    private final AiSupportTool aiSupportTool;
    private final String email;

    public AiSupportSessionTool(AiSupportTool aiSupportTool, String email) {
        this.aiSupportTool = aiSupportTool;
        this.email = email;
    }

    /**
     * 카테고리별 고객센터 안내를 조회합니다.
     */
    @Tool(
            description = "한끼팟 기능, 정책, 사용 방법을 카테고리별로 조회합니다.",
            resultConverter = AiSupportToolResultConverter.class
    )
    public AiSupportGuideToolResult getServiceGuide(
            @ToolParam(description = "문의 카테고리. MATCH, POST, POINT, CHAT, REPORT, ACCOUNT, MEET, GENERAL 중 하나", required = true)
            AiSupportCategory category
    ) {
        return aiSupportTool.getServiceGuide(category);
    }

    /**
     * 현재 로그인 사용자의 기본 정보와 보유 포인트를 조회합니다.
     *
     * email은 LLM이 선택하지 않고 서버에서 고정한 값을 사용합니다.
     */
    @Tool(
            description = "현재 로그인 사용자의 기본 정보와 보유 포인트를 조회합니다. 개인 상태에 맞춘 안내가 필요할 때 사용합니다.",
            resultConverter = AiSupportToolResultConverter.class
    )
    public AiSupportUserContextToolResult getUserSupportContext() {
        return aiSupportTool.getUserSupportContext(email);
    }
}
