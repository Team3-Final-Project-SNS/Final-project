package com.example.team3final.domain.ai.support.tool;

import com.example.team3final.domain.ai.support.enums.AiSupportCategory;
import com.example.team3final.domain.user.dto.response.UserInfoDto;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.service.UserInternalService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 고객센터 AI가 답변 근거를 얻기 위해 호출하는 Spring AI Tool입니다.
 *
 * 기능/정책 안내는 카테고리별 가이드로 제공하고,
 * 개인 상태가 필요한 질문은 로그인 사용자의 포인트와 계정 상태를 조회합니다.
 *
 * 주의:
 * 이 클래스의 public 메서드가 모두 LLM에 직접 노출되는 것은 아닙니다.
 * 실제 채팅 요청에서는 AiSupportSessionTool을 LLM에 넘기고,
 * AiSupportSessionTool이 현재 로그인 사용자의 email을 고정한 뒤 이 클래스를 호출합니다.
 */
@Component
@RequiredArgsConstructor
public class AiSupportTool {

    private static final int MAX_GUIDE_LENGTH = 4_000;

    private final UserInternalService userInternalService;
    private final ResourceLoader resourceLoader;


    /**
     * 카테고리별 고객센터 안내 문서를 조회합니다.
     *
     * LLM은 사용자의 자연어 질문을 MATCH, POST, POINT, CHAT, REPORT,
     * ACCOUNT, MEET, REVIEW, GENERAL 중 하나로 분류한 뒤 이 Tool을 호출합니다.
     * Tool은 카테고리에 매핑된 내부 고객센터 정책 문서를 읽어 반환합니다.
     *
     * @ToolParam은 LLM이 Tool을 호출할 때 어떤 인자를 넣어야 하는지 알려주는 설명서입니다.
     * 여기서는 category가 필수이며, 허용 enum 값을 description에 적어 모델의 잘못된 호출을 줄입니다.
     */
    @Tool(
            description = "한끼팟 기능, 정책, 사용 방법을 내부 정책 문서 기반으로 카테고리별 조회합니다.",
            resultConverter = AiSupportToolResultConverter.class
    )
    public AiSupportGuideToolResult getServiceGuide(
            @ToolParam(description = "문의 카테고리. MATCH, POST, POINT, CHAT, REPORT, ACCOUNT, MEET, REVIEW, GENERAL 중 하나", required = true)
            AiSupportCategory category
    ) {
        AiSupportGuideDocument document = resolveGuideDocument(category);

        // RAG 검색 결과가 없거나 Tool 호출이 필요한 경우에도 같은 정책 문서를 기준으로 답하게 합니다.
        // 즉, 고객센터 정책의 단일 기준은 src/main/resources/rag-docs/support/*.md 입니다.
        return new AiSupportGuideToolResult(
                category,
                document.title(),
                readSupportPolicyDocument(document.path()),
                document.relatedApi(),
                true
        );
    }

    private AiSupportGuideDocument resolveGuideDocument(AiSupportCategory category) {
        // 여러 카테고리가 하나의 정책 문서를 공유할 수 있습니다.
        // 예: MATCH와 POST는 모두 매칭/게시글 정책 문서에서 관리합니다.
        return switch (category) {
            case MATCH -> new AiSupportGuideDocument(
                    "매칭 신청 및 취소 안내",
                    "matching-policy.md",
                    "/api/v1/matches"
            );
            case POST -> new AiSupportGuideDocument(
                    "게시글 작성 및 관리 안내",
                    "matching-policy.md",
                    "/api/v1/posts"
            );
            case POINT -> new AiSupportGuideDocument(
                    "포인트와 정산 안내",
                    "point-policy.md",
                    "/api/v1/point-transactions"
            );
            case CHAT -> new AiSupportGuideDocument(
                    "채팅 및 알림 안내",
                    "chat-notification-policy.md",
                    "/api/v1/chat"
            );
            case REPORT -> new AiSupportGuideDocument(
                    "신고 접수 및 처리 안내",
                    "report-policy.md",
                    "/api/v1/reports"
            );
            case ACCOUNT -> new AiSupportGuideDocument(
                    "회원가입, 로그인, 계정 안내",
                    "account-policy.md",
                    "/api/v1/auth, /api/v1/users"
            );
            case MEET -> new AiSupportGuideDocument(
                    "만남 인증과 노쇼 안내",
                    "no-show-policy.md",
                    "/api/v1/meets"
            );
            case REVIEW -> new AiSupportGuideDocument(
                    "후기와 매너온도 안내",
                    "review-policy.md",
                    "/api/v1/reviews"
            );
            case GENERAL -> new AiSupportGuideDocument(
                    "고객센터 이용 안내",
                    "account-policy.md",
                    "/api/v1/inquiries"
            );
        };
    }

    private String readSupportPolicyDocument(String filename) {
        Resource resource = resourceLoader.getResource("classpath:rag-docs/support/" + filename);

        try {
            if (!resource.exists()) {
                // 문서 누락은 Tool 실패로 던지지 않고 명시적인 안내 문구로 반환합니다.
                // 그래야 LLM이 없는 정책을 지어내지 않고 사용자에게 확인 필요 상황을 설명할 수 있습니다.
                return "해당 카테고리의 내부 정책 문서를 찾을 수 없습니다. 확인이 필요한 내용은 1:1 문의로 안내하세요.";
            }

            String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            // 긴 문서를 그대로 Tool 결과로 넘기면 토큰 비용이 커지므로 상한을 둡니다.
            return truncate(content, MAX_GUIDE_LENGTH);
        } catch (IOException e) {
            return "내부 정책 문서를 읽는 중 오류가 발생했습니다. 정책을 단정하지 말고 1:1 문의 또는 관리자 확인을 안내하세요.";
        }
    }

    /**
     * 로그인 사용자의 개인 상태를 조회합니다.
     *
     * 보유 포인트, 계정 상태, 닉네임처럼 개인 맞춤 안내에 필요한 정보가 있을 때
     * LLM이 이 Tool을 호출합니다. 비밀번호, 토큰, 민감 인증 정보는 반환하지 않고,
     * 답변에 필요한 최소 사용자 컨텍스트만 제공합니다.
     */
    public AiSupportUserContextToolResult getUserSupportContext(String email) {
        // email은 LLM이 입력한 값이 아니라 인증된 사용자 정보에서 온 값입니다.
        // 다른 사용자의 포인트/상태를 질문해도 현재 로그인 사용자 컨텍스트만 조회됩니다.
        User user = userInternalService.findByEmail(email);
        UserInfoDto userInfo = userInternalService.getUserInfo(user.getId());

        return new AiSupportUserContextToolResult(
                userInfo.userId(),
                userInfo.nickname(),
                userInfo.major(),
                userInfo.studentNumber(),
                user.getTotalPoint(),
                user.getStatus().name()
        );
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength);
    }

    private record AiSupportGuideDocument(
            String title,
            String path,
            String relatedApi
    ) {
    }
}
