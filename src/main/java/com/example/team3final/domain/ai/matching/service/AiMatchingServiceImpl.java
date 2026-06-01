package com.example.team3final.domain.ai.matching.service;


import com.example.team3final.common.config.AiProperties;
import com.example.team3final.common.exception.AiException;
import com.example.team3final.domain.ai.common.entity.AiCallMetric;
import com.example.team3final.domain.ai.common.enums.AiCallStatus;
import com.example.team3final.domain.ai.common.enums.AiErrorType;
import com.example.team3final.domain.ai.common.enums.AiFeature;
import com.example.team3final.domain.ai.common.enums.AiPromptType;
import com.example.team3final.domain.ai.common.repository.AiCallMetricRepository;
import com.example.team3final.domain.ai.matching.dto.request.AiMatchingChatRequestDto;
import com.example.team3final.domain.ai.matching.dto.response.AiMatchingChatResponseDto;
import com.example.team3final.domain.ai.matching.dto.response.RecommendedPostDto;
import com.example.team3final.domain.ai.matching.tool.AiMatchingPostToolResult;
import com.example.team3final.domain.ai.matching.tool.AiMatchingTool;
import com.example.team3final.domain.ai.matching.tool.AiMatchingToolResultConverter;
import com.example.team3final.domain.ai.prompt.service.AiPromptFileService;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 매칭 AI 서비스 구현체입니다.
 *
 * 로그인한 사용자의 학교, 보유 포인트, 신청 이력 정보를 기준으로
 * 모집 중인 식사팟 후보를 조회하고, 해당 후보 정보를 프롬프트에 주입하여
 * LLM이 자연어 추천 응답을 생성하도록 처리합니다.
 *
 * 주요 처리 흐름:
 * 1. 로그인 사용자 조회
 * 2. 같은 학교의 모집 중인 게시글 후보 조회
 * 3. 신청 가능 여부와 책임비 포인트 충족 여부 검증
 * 4. DB에서 활성 프롬프트 템플릿을 조회하고 프롬프트 파일 렌더링
 * 5. ChatClient를 통해 AI 추천 답변 생성
 * 6. 성공/실패/fallback 상태를 AiCallMetric으로 저장
 *
 * AI 호출 또는 Tool 조회 실패 시 핵심 서비스가 중단되지 않도록
 * fallback 응답을 반환합니다.
 */

@Slf4j
@Service
public class AiMatchingServiceImpl implements AiMatchingService {

    private final ChatClient chatClient;
    private final AiPromptFileService aiPromptFileService;
    private final AiMatchingTool aiMatchingTool;
    private final AiCallMetricRepository aiCallMetricRepository;
    private final AiProperties aiProperties;
    private final UserService userService;


    public AiMatchingServiceImpl(
            ChatClient.Builder chatClientBuilder,
            AiPromptFileService aiPromptFileService,
            AiMatchingTool aiMatchingTool,
            AiCallMetricRepository aiCallMetricRepository,
            AiProperties aiProperties,
            UserService userService
    ) {
        this.chatClient = chatClientBuilder.build();
        this.aiPromptFileService = aiPromptFileService;
        this.aiMatchingTool = aiMatchingTool;
        this.aiCallMetricRepository = aiCallMetricRepository;
        this.aiProperties = aiProperties;
        this.userService = userService;

    }


    /**
     * 사용자의 자연어 조건에 대한 매칭 AI 응답을 생성합니다.
     *
     * 같은 학교의 모집 중인 식사팟 후보를 Tool로 조회한 뒤,
     * 후보 목록과 사용자 정보를 프롬프트 변수로 주입하여 LLM 응답을 생성합니다.
     *
     * Tool 조회 실패, 프롬프트 로드 실패, LLM 호출 실패 상황에서는
     * 사용자에게 자연어 fallback 응답을 반환하고, 실패 상태를 메트릭으로 저장합니다.
     *
     * @param email 로그인한 사용자 이메일
     * @param request 사용자의 자연어 식사 조건 요청
     * @return AI 추천 답변, 추천 후보 목록, fallback 사용 여부
     */
    @Override
    public AiMatchingChatResponseDto chat(String email, AiMatchingChatRequestDto request) {

        // AI 호출 1건을 추적하기 위한 고유 요청 ID입니다.
        // 로그, 메트릭, 장애 분석에서 같은 요청 흐름을 식별하는 데 사용합니다.
        // 이렇게 가능
        // requestId=A / userId=3 / SUCCESS / latency=2초
        // requestId=B / userId=3 / SUCCESS / latency=8초
        // requestId=C / userId=3 / FALLBACK / error=TOOL_ERROR
        String requestId = UUID.randomUUID().toString();

        long startedAt = System.currentTimeMillis();
        Long userId = null;
        Integer promptTokens = null;
        Integer completionTokens = null;
        Integer totalTokens = null;

        try {
            User user = userService.findByEmail(email);
            userId = user.getId();

            String candidatePosts;
            String toolResults;
            List<AiMatchingPostToolResult> candidates;
            boolean toolFallbackUsed = false;

            try {
                candidates = aiMatchingTool.searchRecruitingMealPosts(
                        user.getId(),
                        user.getUniversityId(),
                        user.getTotalPoint(),
                        request.message()
                );

                candidatePosts = new AiMatchingToolResultConverter()
                        .convert(candidates, List.class);

                toolResults = "모집글 조회 도구 호출 성공";
                // Tool 조회는 정상적으로 끝났지만 조건에 맞는 모집글 후보가 없는 경우.
                // 이 상태에서 LLM을 호출하면 후보에 없는 게시글을 생성해 추천할 수 있으므로,
                // LLM 호출 전에 바로 "추천 결과 없음" 응답을 반환하여 환각을 방지한다.
                // recommendedPosts도 빈 목록으로 내려가므로 프론트는 게시글 바로가기 버튼을 만들지 않는다.
                if (candidates.isEmpty()) {
                    saveMetric(
                            requestId,
                            userId,
                            startedAt,
                            AiCallStatus.SUCCESS,
                            null,
                            null,
                            null,
                            null,
                            null
                    );

                    return new AiMatchingChatResponseDto(
                            request.conversationId(),
                            "현재 조건에 맞는 식사팟을 찾지 못했어요. 시간대, 메뉴, 분위기 조건을 조금 넓혀보면 더 많은 모집글을 찾을 수 있어요.",
                            List.of(),
                            false
                    );
                }
            } catch (Exception e) {
                log.error("[AiMatchingService] 모집글 조회 Tool 호출 실패", e);


                toolFallbackUsed = true;
                candidates = List.of();
                candidatePosts = "모집글 후보를 조회하지 못했습니다.";
                toolResults = "모집글 조회 도구 호출에 실패했습니다. 신청 가능 여부는 확인하지 못했습니다.";
            }

            String systemPrompt = aiPromptFileService.render(
                    AiPromptType.MATCHING_CHAT,
                    Map.of(
                            "userMessage", request.message(),
                            "userId", user.getId(),
                            "universityId", user.getUniversityId(),
                            "userPoint", user.getTotalPoint(),
                            "conversationContext", "이전 대화 없음",
                            "candidatePosts", candidatePosts,
                            "toolResults", toolResults
                    )
            );

            ChatResponse chatResponse = chatClient.prompt()
                    .system(systemPrompt)
                    .user(request.message())
//                    .tools(aiMatchingTool)
                    .call()
                    .chatResponse();

            String answer = extractContent(chatResponse);
            TokenUsage tokenUsage = extractTokenUsage(chatResponse);
            promptTokens = tokenUsage.promptTokens();
            completionTokens = tokenUsage.completionTokens();
            totalTokens = tokenUsage.totalTokens();

            List<RecommendedPostDto> recommendedPosts = candidates.stream()
                    .map(candidate -> new RecommendedPostDto(
                            candidate.postId(),
                            candidate.placeName(),
                            candidate.meetAt(),
                            candidate.deposit(),
                            "AI 추천 후보입니다.",
                            candidate.applicationAvailable(),
                            candidate.pointAffordable()
                    ))
                    .toList();

            saveMetric(
                    requestId,
                    user.getId(),
                    startedAt,
                    toolFallbackUsed ? AiCallStatus.FALLBACK : AiCallStatus.SUCCESS,
                    toolFallbackUsed ? AiErrorType.TOOL_ERROR : null,
                    toolFallbackUsed ? "모집글 조회 도구 호출 실패" : null,
                    promptTokens,
                    completionTokens,
                    totalTokens
            );

            return new AiMatchingChatResponseDto(
                    request.conversationId(),
                    answer,
                    recommendedPosts,
                    toolFallbackUsed
            );

        } catch (AiException e) {

            saveMetric(
                    requestId,
                    userId,
                    startedAt,
                    AiCallStatus.FALLBACK,
                    AiErrorType.PROMPT_LOAD_ERROR,
                    e.getMessage(),
                    promptTokens,
                    completionTokens,
                    totalTokens
            );

            return new AiMatchingChatResponseDto(
                    request.conversationId(),
                    "AI 추천 기능을 잠시 사용할 수 없습니다. 대신 모집글 목록에서 직접 조건에 맞는 식사팟을 확인해주세요.",
                    List.of(),
                    true
            );

        } catch (Exception e) {

            saveMetric(
                    requestId,
                    userId,
                    startedAt,
                    AiCallStatus.FALLBACK,
                    resolveErrorType(e),
                    e.getMessage(),
                    promptTokens,
                    completionTokens,
                    totalTokens
            );

            return new AiMatchingChatResponseDto(
                    request.conversationId(),
                    "현재 AI 매칭 응답 생성이 원활하지 않습니다. 잠시 후 다시 시도해주세요.",
                    List.of(),
                    true
            );
        }
    }

    /**
     * AI 호출 결과를 메트릭으로 저장합니다.
     *
     * 요청 ID, 사용자 ID, 기능명, 모델명, 응답 지연 시간,
     * 처리 상태, 에러 유형과 메시지를 저장하여
     * 추후 비용 추적, 장애 분석, 대시보드 구성에 활용합니다.
     */
    private void saveMetric(
            String requestId,
            Long userId,
            long startedAt,
            AiCallStatus status,
            AiErrorType errorType,
            String errorMessage,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens
    ) {
        aiCallMetricRepository.save(
                AiCallMetric.builder()
                        .requestId(requestId)
                        .userId(userId)
                        .feature(AiFeature.MATCHING)
                        .model(aiProperties.getMatching().getModel())
                        .promptTokens(promptTokens)
                        .completionTokens(completionTokens)
                        .totalTokens(totalTokens)
                        .latencyMs(System.currentTimeMillis() - startedAt)
                        .status(status)
                        .errorType(errorType)
                        .errorMessage(truncate(errorMessage))
                        .build()
        );
    }

    private String extractContent(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null) {
            return "AI 응답을 생성하지 못했습니다. 잠시 후 다시 시도해주세요.";
        }

        return chatResponse.getResult().getOutput().getText();
    }

    private TokenUsage extractTokenUsage(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getMetadata() == null) {
            return TokenUsage.empty();
        }

        Usage usage = chatResponse.getMetadata().getUsage();
        if (usage == null) {
            return TokenUsage.empty();
        }

        return new TokenUsage(
                usage.getPromptTokens(),
                usage.getCompletionTokens(),
                usage.getTotalTokens()
        );
    }

    private AiErrorType resolveErrorType(Exception e) {
        if (e instanceof AiException) {
            return AiErrorType.PROMPT_LOAD_ERROR;
        }

        if (hasStackTraceClassContaining(e, ".domain.ai.matching.tool.") || containsIgnoreCase(e.getMessage(), "tool")) {
            return AiErrorType.TOOL_ERROR;
        }

        return AiErrorType.SERVER_ERROR;
    }

    private boolean hasStackTraceClassContaining(Throwable throwable, String keyword) {
        Throwable current = throwable;
        while (current != null) {
            for (StackTraceElement element : current.getStackTrace()) {
                if (element.getClassName().contains(keyword)) {
                    return true;
                }
            }
            current = current.getCause();
        }

        return false;
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword.toLowerCase());
    }

    private record TokenUsage(
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens
    ) {
        private static TokenUsage empty() {
            return new TokenUsage(null, null, null);
        }
    }

    /**
     * 메트릭에 저장할 에러 메시지를 컬럼 길이에 맞게 제한합니다.
     */
    private String truncate(String message) {
        if (message == null) {
            return null;
        }

        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
