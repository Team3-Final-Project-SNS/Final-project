package com.example.team3final.domain.ai.support.tool;

import com.example.team3final.domain.ai.support.enums.AiSupportCategory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 고객센터 AI 요청 1회에 바인딩되는 Tool 어댑터입니다.
 *
 * LLM에는 현재 로그인 사용자의 이메일을 직접 입력받는 Tool을 노출하지 않고,
 * 서버가 인증 객체에서 얻은 email을 고정해서 사용자 컨텍스트를 조회합니다.
 *
 * 이렇게 분리하는 이유:
 * - LLM이 Tool 파라미터로 다른 사용자의 email을 만들어 넣는 것을 막습니다.
 * - 실제 조회 로직은 AiSupportTool에 모아두고, 요청별 보안 컨텍스트만 여기서 주입합니다.
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
     *
     * LLM은 자연어 질문을 카테고리 enum으로 분류한 뒤 이 Tool을 호출합니다.
     * 반환값은 AiSupportToolResultConverter를 거쳐 모델이 읽을 수 있는 텍스트로 바뀝니다.
     */
    @Tool(
            description = "한끼팟 기능, 정책, 사용 방법을 카테고리별로 조회합니다.",
            resultConverter = AiSupportToolResultConverter.class
    )
    public AiSupportGuideToolResult getServiceGuide(
            @ToolParam(description = "문의 카테고리. MATCH, POST, POINT, CHAT, REPORT, ACCOUNT, MEET, REVIEW, GENERAL 중 하나", required = true)
            AiSupportCategory category
    ) {
        return aiSupportTool.getServiceGuide(category);
    }

    /**
     * 현재 로그인 사용자의 기본 정보와 보유 포인트를 조회합니다.
     *
     * email은 LLM이 선택하지 않고 서버에서 고정한 값을 사용합니다.
     * 그래서 사용자가 "다른 사람 포인트 알려줘"라고 물어도 현재 로그인 사용자 정보만 반환됩니다.
     */
    @Tool(
            description = "현재 로그인 사용자의 기본 정보와 보유 포인트를 조회합니다. 개인 상태에 맞춘 안내가 필요할 때 사용합니다.",
            resultConverter = AiSupportToolResultConverter.class
    )
    public AiSupportUserContextToolResult getUserSupportContext() {
        return aiSupportTool.getUserSupportContext(email);
    }
}
