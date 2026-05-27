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
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    @Override
    public AiMatchingChatResponseDto chat(String email, AiMatchingChatRequestDto request) {
        String requestId = UUID.randomUUID().toString();
        long startedAt = System.currentTimeMillis();

        try {
            User user = userService.findByEmail(email);

            String candidatePosts;
            String toolResults;
            List<AiMatchingPostToolResult> candidates;
            boolean toolFallbackUsed = false;

            try {
                candidates = aiMatchingTool.searchRecruitingMealPosts(
                        user.getId(),
                        user.getUniversityId(),
                        user.getPoint(),
                        request.message()
                );

                candidatePosts = new AiMatchingToolResultConverter()
                        .convert(candidates, List.class);

                toolResults = "모집글 조회 도구 호출 성공";
            } catch (Exception e) {
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
                            "userPoint", user.getPoint(),
                            "conversationContext", "이전 대화 없음",
                            "candidatePosts", candidatePosts,
                            "toolResults", toolResults
                    )
            );

            String answer = chatClient.prompt()
                    .system(systemPrompt)
                    .user(request.message())
//                    .tools(aiMatchingTool)
                    .call()
                    .content();

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
                    toolFallbackUsed ? "모집글 조회 도구 호출 실패" : null
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
                    null,
                    startedAt,
                    AiCallStatus.FALLBACK,
                    AiErrorType.PROMPT_LOAD_ERROR,
                    e.getMessage()
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
                    null,
                    startedAt,
                    AiCallStatus.FALLBACK,
                    AiErrorType.SERVER_ERROR,
                    e.getMessage()
            );

            return new AiMatchingChatResponseDto(
                    request.conversationId(),
                    "현재 AI 매칭 응답 생성이 원활하지 않습니다. 잠시 후 다시 시도해주세요.",
                    List.of(),
                    true
            );
        }
    }

    private void saveMetric(
            String requestId,
            Long userId,
            long startedAt,
            AiCallStatus status,
            AiErrorType errorType,
            String errorMessage
    ) {
        aiCallMetricRepository.save(
                AiCallMetric.builder()
                        .requestId(requestId)
                        .userId(userId)
                        .feature(AiFeature.MATCHING)
                        .model(aiProperties.getMatching().getModel())
                        .latencyMs(System.currentTimeMillis() - startedAt)
                        .status(status)
                        .errorType(errorType)
                        .errorMessage(truncate(errorMessage))
                        .build()
        );
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }

        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
