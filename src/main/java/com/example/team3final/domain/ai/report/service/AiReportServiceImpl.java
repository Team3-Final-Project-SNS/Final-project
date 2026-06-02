package com.example.team3final.domain.ai.report.service;

import com.example.team3final.common.config.AiProperties;
import com.example.team3final.common.exception.AiException;
import com.example.team3final.domain.admin.service.AdminService;
import com.example.team3final.domain.ai.common.enums.AiCallStatus;
import com.example.team3final.domain.ai.common.enums.AiErrorType;
import com.example.team3final.domain.ai.common.enums.AiFeature;
import com.example.team3final.domain.ai.common.enums.AiPromptType;
import com.example.team3final.domain.ai.common.service.AiCallMetricService;
import com.example.team3final.domain.ai.prompt.service.AiPromptFileService;
import com.example.team3final.domain.ai.report.dto.request.AiReportChatRequestDto;
import com.example.team3final.domain.ai.report.dto.response.*;
import com.example.team3final.domain.ai.report.entity.AiReportSummary;
import com.example.team3final.domain.ai.report.enums.AiReportChatAction;
import com.example.team3final.domain.ai.report.enums.AiReportDecisionSuggestion;
import com.example.team3final.domain.ai.report.enums.AiReportRiskLevel;
import com.example.team3final.domain.ai.report.repository.AiReportSummaryRepository;
import com.example.team3final.domain.ai.report.tool.AiReportHighRiskUserToolResult;
import com.example.team3final.domain.ai.report.tool.AiReportTool;
import com.example.team3final.domain.report.entity.Report;
import com.example.team3final.domain.report.service.ReportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 신고 AI 분석의 핵심 비즈니스 로직을 담당하는 서비스 구현체입니다.
 *
 * 관리자 검증, 프롬프트 로딩, Spring AI ChatClient 호출, Tool 연결,
 * 분석 결과 저장, AI 호출 지표 기록, 실패 시 fallback 응답 생성을 처리합니다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class AiReportServiceImpl implements AiReportService {

    private static final int DEFAULT_HIGH_RISK_USER_LIMIT = 5;
    private static final int MAX_HIGH_RISK_USER_LIMIT = 20;
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");


    /**
     * REPORT_SUMMARY 프롬프트 로딩 실패 시 사용하는 fallback 프롬프트입니다.
     * DB 템플릿 또는 프롬프트 파일 장애가 발생해도 신고 AI 기능이 완전히 중단되지 않도록
     * 최소한의 신고 분석 규칙을 제공합니다.
     */
    private static final String DEFAULT_REPORT_PROMPT = """
            너는 한끼팟 관리자 전용 신고 분석 AI다.
            반드시 제공된 Tool을 먼저 호출해서 신고 원문과 누적 신고 맥락을 확인한다.
            RAG나 외부 지식은 사용하지 말고 Tool 결과와 현재 서비스 제재 정책만 근거로 판단한다.
            AI 판단은 최종 처분이 아니라 관리자 의사결정을 돕는 참고 의견이다.

            처리 제안 enum:
            - ACCEPT: 신고 채택 권고
            - REJECT: 신고 기각 권고
            - NEEDS_REVIEW: 추가 확인 권고

            위험도 enum:
            - LOW: 근거 부족 또는 단순 오신고 가능성
            - MEDIUM: 정책 위반 가능성이 있으나 추가 확인 필요
            - HIGH: 반복 신고, 명확한 위반 정황, 피해 가능성이 큰 경우

            서비스 제재 정책:
            - 신고 채택 시 신고자에게 50P 포상
            - 피신고자 채택 누적 1~2회: 경고 수준
            - 채택 누적 3회: 3일 정지
            - 채택 누적 4회: 10일 정지
            - 채택 누적 5회: 30일 정지
            - 채택 누적 6회 이상: 영구 정지

            응답은 요청받은 Java record 스키마에 맞춰 한국어로 작성한다.
            confidenceScore는 0~100 정수로 작성한다.
            """;

    private final ChatClient chatClient;
    private final AiPromptFileService aiPromptFileService;
    private final AiReportTool aiReportTool;
    private final AiReportSummaryRepository aiReportSummaryRepository;
    private final AiCallMetricService aiCallMetricService;
    private final AiProperties aiProperties;
    private final AdminService adminService;
    private final ReportService reportService;

    public AiReportServiceImpl(
            ChatClient.Builder chatClientBuilder,
            AiPromptFileService aiPromptFileService,
            AiReportTool aiReportTool,
            AiReportSummaryRepository aiReportSummaryRepository,
            AiCallMetricService aiCallMetricService,
            AiProperties aiProperties,
            AdminService adminService,
            ReportService reportService
    ) {
        this.chatClient = chatClientBuilder.build();
        this.aiPromptFileService = aiPromptFileService;
        this.aiReportTool = aiReportTool;
        this.aiReportSummaryRepository = aiReportSummaryRepository;
        this.aiCallMetricService = aiCallMetricService;
        this.aiProperties = aiProperties;
        this.adminService = adminService;
        this.reportService = reportService;
    }


    /**
     * 관리자 신고 AI 챗봇 요청을 처리합니다.
     * 자연어 메시지를 의도 분류한 뒤 신고 단건 분석, 고위험 유저 조회,
     * 추가 입력 요청 중 하나로 라우팅합니다.
     */
    @Override
    @Transactional
    public AiReportChatResponseDto chat(Long adminId, AiReportChatRequestDto request) {
        validateAdmin(adminId);

        AiReportChatIntentResult intent = classifyChatIntent(request.message());
        AiReportChatAction action = resolveChatAction(intent);

        if (action == AiReportChatAction.ANALYZE_REPORT) {
            if (intent == null || intent.reportId() == null) {
                return clarify("분석할 신고 ID를 알려주세요. 예: 12번 신고 분석해줘");
            }

            AiReportAnalysisResponseDto analysis = analyzeReport(adminId, intent.reportId());
            return new AiReportChatResponseDto(
                    buildAnalysisChatAnswer(analysis),
                    AiReportChatAction.ANALYZE_REPORT,
                    analysis,
                    null,
                    analysis.fallbackUsed()
            );
        }

        if (action == AiReportChatAction.HIGH_RISK_USERS) {
            int limit = normalizeLimit(intent == null ? null : intent.limit());
            AiReportHighRiskUsersResponseDto highRiskUsers = getHighRiskUsers(adminId, limit);
            return new AiReportChatResponseDto(
                    highRiskUsers.answer(),
                    AiReportChatAction.HIGH_RISK_USERS,
                    null,
                    highRiskUsers,
                    highRiskUsers.fallbackUsed()
            );
        }

        return clarify(requiredText(
                intent == null ? null : intent.clarificationMessage(),
                "신고 ID를 지정해 분석을 요청하거나, 고위험 유저 조회를 요청해주세요."
        ));
    }


    /**
     * 특정 신고 건을 AI로 분석합니다.
     *
     * 관리자 권한을 검증한 뒤 신고 정보를 조회하고, REPORT_SUMMARY 프롬프트를 렌더링하여
     * LLM에 system prompt로 주입합니다. 이후 AiReportTool을 통해 신고 원문, 대상 정보,
     * 누적 신고 맥락을 조회하게 하고, AI 응답을 AiReportSummary로 저장합니다.
     *
     * 성공/실패 여부, 응답 시간, 토큰 사용량, 프롬프트 버전 정보는 AiCallMetric에 기록합니다.
     * AI 호출 또는 Tool 처리 중 예외가 발생하면 fallback 분석 결과를 저장하고
     * 관리자가 직접 검토할 수 있는 안내 문구를 반환합니다.
     */
    @Override
    @Transactional
    public AiReportAnalysisResponseDto analyzeReport(Long adminId, Long reportId) {
        validateAdmin(adminId);

        String requestId = UUID.randomUUID().toString();
        long startedAt = System.currentTimeMillis();
        Long promptTemplateId = null;
        String promptVersion = null;
        Integer promptTokens = null;
        Integer completionTokens = null;
        Integer totalTokens = null;

        try {
            Report report = reportService.getReportById(reportId);
            AiPromptFileService.RenderedPrompt prompt = renderPrompt(reportId, adminId);
            promptTemplateId = prompt.promptTemplateId();
            promptVersion = prompt.version();

            ResponseEntity<ChatResponse, AiReportLlmResult> response = chatClient
                    .prompt()
                    .system(prompt.content())
                    .user("신고 ID " + reportId + "번을 분석하고 관리자 조치 방향을 제안해줘.")
                    .options(OpenAiChatOptions.builder()
                            .model(aiProperties.getReport().getModel())
                            .maxTokens(aiProperties.getReport().getMaxTokens())
                            .temperature(aiProperties.getReport().getTemperature())
                            .build())
                    .tools(aiReportTool)
                    .call()
                    .responseEntity(AiReportLlmResult.class);

            AiReportLlmResult result = response.entity();
            TokenUsage tokenUsage = extractTokenUsage(response.response());
            promptTokens = tokenUsage.promptTokens();
            completionTokens = tokenUsage.completionTokens();
            totalTokens = tokenUsage.totalTokens();

            AiReportSummary savedSummary = aiReportSummaryRepository.save(
                    AiReportSummary.builder()
                            .reportId(report.getId())
                            .adminId(adminId)
                            .requestId(requestId)
                            .reportReason(report.getReason())
                            .decisionSuggestion(resolveDecision(result))
                            .riskLevel(resolveRiskLevel(result))
                            .summary(truncate(requiredText(result.summary(), "신고 내용을 요약하지 못했습니다."), 2000))
                            .evidence(truncate(result.evidence(), 500))
                            .recommendationReason(truncate(result.recommendationReason(), 1000))
                            .confidenceScore(resolveConfidence(result.confidenceScore()))
                            .needsAdminReview(resolveNeedsReview(result))
                            .fallbackUsed(false)
                            .model(aiProperties.getReport().getModel())
                            .promptTemplateId(promptTemplateId)
                            .promptVersion(promptVersion)
                            .build()
            );

            saveMetric(
                    requestId,
                    adminId,
                    startedAt,
                    AiCallStatus.SUCCESS,
                    null,
                    null,
                    promptTemplateId,
                    promptVersion,
                    promptTokens,
                    completionTokens,
                    totalTokens
            );

            return AiReportAnalysisResponseDto.of(
                    savedSummary,
                    requiredText(result.actionGuide(), buildActionGuide(savedSummary.getDecisionSuggestion()))
            );
        } catch (Exception e) {
            log.error("[AiReportService] 신고 AI 분석 실패", e);

            saveMetric(
                    requestId,
                    adminId,
                    startedAt,
                    AiCallStatus.FALLBACK,
                    resolveErrorType(e),
                    e.getMessage(),
                    promptTemplateId,
                    promptVersion,
                    promptTokens,
                    completionTokens,
                    totalTokens
            );

            AiReportSummary fallbackSummary = saveFallbackSummary(
                    requestId,
                    adminId,
                    reportId,
                    promptTemplateId,
                    promptVersion
            );
            return AiReportAnalysisResponseDto.of(
                    fallbackSummary,
                    "AI 분석에 실패했습니다. 신고 원문, 대상 게시글, 누적 신고 이력을 관리자가 직접 확인한 뒤 기존 신고 처리 API로 채택 또는 기각을 결정해주세요."
            );
        }
    }

    /**
     * 누적 신고 데이터를 기반으로 고위험 유저 후보를 조회합니다.
     *
     * REPORT_SUMMARY 프롬프트와 AiReportTool을 사용해 최근 신고 이력, 처리 상태,
     * 채택 횟수 등을 기반으로 위험 유저 후보를 생성합니다.
     *
     * AI 응답 생성에 실패하면 Tool 조회 결과를 직접 사용해 fallback 후보 목록을 반환합니다.
     * 이 경우에도 AiCallMetric에 실패 상태와 에러 유형을 기록하여 모니터링할 수 있게 합니다.
     */
    @Override
    public AiReportHighRiskUsersResponseDto getHighRiskUsers(Long adminId, int limit) {
        validateAdmin(adminId);

        String requestId = UUID.randomUUID().toString();
        long startedAt = System.currentTimeMillis();
        Long promptTemplateId = null;
        String promptVersion = null;
        Integer promptTokens = null;
        Integer completionTokens = null;
        Integer totalTokens = null;

        try {
            AiPromptFileService.RenderedPrompt prompt = renderPrompt(null, adminId);
            promptTemplateId = prompt.promptTemplateId();
            promptVersion = prompt.version();

            ResponseEntity<ChatResponse, List<AiReportHighRiskUserDto>> response = chatClient
                    .prompt()
                    .system(prompt.content())
                    .user("""
                            최근 신고 데이터를 Tool로 조회해서 고위험군 유저 후보를 골라줘.
                            위험도, 근거 요약, 관리자 권장 조치를 포함해줘.
                            후보 수는 %d명 이하로 제한해줘.
                            """.formatted(limit))
                    .options(OpenAiChatOptions.builder()
                            .model(aiProperties.getReport().getModel())
                            .maxTokens(aiProperties.getReport().getMaxTokens())
                            .temperature(aiProperties.getReport().getTemperature())
                            .build())
                    .tools(aiReportTool)
                    .call()
                    .responseEntity(new ParameterizedTypeReference<List<AiReportHighRiskUserDto>>() {
                    });

            List<AiReportHighRiskUserDto> users = response.entity();
            TokenUsage tokenUsage = extractTokenUsage(response.response());
            promptTokens = tokenUsage.promptTokens();
            completionTokens = tokenUsage.completionTokens();
            totalTokens = tokenUsage.totalTokens();

            saveMetric(
                    requestId,
                    adminId,
                    startedAt,
                    AiCallStatus.SUCCESS,
                    null,
                    null,
                    promptTemplateId,
                    promptVersion,
                    promptTokens,
                    completionTokens,
                    totalTokens
            );

            return new AiReportHighRiskUsersResponseDto(
                    "고위험군 유저 후보 조회가 완료되었습니다.",
                    users == null ? List.of() : users,
                    false
            );
        } catch (Exception e) {
            log.error("[AiReportService] 고위험군 유저 AI 조회 실패", e);

            saveMetric(
                    requestId,
                    adminId,
                    startedAt,
                    AiCallStatus.FALLBACK,
                    resolveErrorType(e),
                    e.getMessage(),
                    promptTemplateId,
                    promptVersion,
                    promptTokens,
                    completionTokens,
                    totalTokens
            );

            List<AiReportHighRiskUserDto> fallbackUsers = aiReportTool.findHighRiskUserCandidates(limit)
                    .stream()
                    .map(this::toFallbackHighRiskUser)
                    .toList();

            return new AiReportHighRiskUsersResponseDto(
                    "AI 판단 생성에 실패해 Tool 조회 결과를 기준으로 고위험군 후보를 반환합니다.",
                    fallbackUsers,
                    true
            );
        }
    }

    /**
     * 신고 AI 분석에 사용할 REPORT_SUMMARY 프롬프트를 렌더링합니다.
     *
     * 정상 흐름에서는 DB에 등록된 활성 프롬프트 템플릿과 프롬프트 파일을 읽어
     * reportId, adminId 변수를 주입한 최종 system prompt를 생성합니다.
     *
     * 프롬프트 템플릿이 없거나 파일을 읽지 못하면 DEFAULT_REPORT_PROMPT를 반환하여
     * 신고 AI 기능이 완전히 중단되지 않도록 합니다.
     */
    private AiPromptFileService.RenderedPrompt renderPrompt(Long reportId, Long adminId) {
        try {
            return aiPromptFileService.renderWithMetadata(
                    AiPromptType.REPORT_SUMMARY,
                    Map.of(
                            "reportId", reportId == null ? "미지정" : reportId,
                            "adminId", adminId
                    )
            );
        } catch (AiException e) {
            log.warn("[AiReportService] REPORT_SUMMARY 프롬프트 로드 실패. 기본 fallback 프롬프트를 사용합니다.", e);
            return new AiPromptFileService.RenderedPrompt(DEFAULT_REPORT_PROMPT, null, null);
        }
    }

    /**
     * 관리자 자연어 메시지의 의도를 AI로 분류합니다.
     *
     * 메시지를 ANALYZE_REPORT, HIGH_RISK_USERS, CLARIFY 중 하나로 분류하고,
     * 신고 ID 또는 조회 제한 수 같은 부가 정보를 함께 추출합니다.
     *
     * 의도 분류 AI 호출에 실패하면 fallbackClassifyChatIntent를 사용해
     * 정규식 기반으로 최소한의 의도 분류를 수행합니다.
     */
    private AiReportChatIntentResult classifyChatIntent(String message) {
        try {
            AiReportChatIntentResult intent = chatClient
                    .prompt()
                    .system("""
                            너는 관리자 신고 AI 챗봇의 라우터다.
                            사용자의 자연어 메시지를 보고 실행할 기능을 하나만 고른다.

                            action enum:
                            - ANALYZE_REPORT: 특정 신고 ID 1건을 분석해 달라는 요청
                            - HIGH_RISK_USERS: 신고 누적 기반 고위험 유저 후보를 보여 달라는 요청
                            - CLARIFY: 신고 ID가 없거나 요청이 불명확해서 추가 질문이 필요한 경우

                            규칙:
                            - "12번 신고", "신고 12 분석"처럼 숫자와 신고 분석 의도가 있으면 ANALYZE_REPORT로 판단하고 reportId에 숫자를 넣는다.
                            - "고위험", "위험 유저", "신고 많은 유저", "블랙리스트 후보"처럼 유저 목록 요청이면 HIGH_RISK_USERS로 판단한다.
                            - HIGH_RISK_USERS에서 인원 숫자가 있으면 limit에 넣고, 없으면 5를 넣는다.
                            - ANALYZE_REPORT인데 신고 ID 숫자가 없으면 CLARIFY로 판단한다.
                            - 응답은 요청받은 Java record 스키마에 맞춘다.
                            """)
                    .user(message)
                    .options(OpenAiChatOptions.builder()
                            .model(aiProperties.getReport().getModel())
                            .maxTokens(aiProperties.getReport().getMaxTokens())
                            .temperature(0.0)
                            .build())
                    .call()
                    .entity(AiReportChatIntentResult.class);

            return intent == null ? fallbackClassifyChatIntent(message) : intent;
        } catch (Exception e) {
            log.warn("[AiReportService] 신고 AI 챗봇 의도 분류 실패. fallback 분류를 사용합니다.", e);
            return fallbackClassifyChatIntent(message);
        }
    }

    /**
     * AI 의도 분류 실패 시 사용하는 정규식 기반 fallback 분류 로직입니다.
     *
     * 메시지에 포함된 숫자와 핵심 키워드를 기준으로 신고 단건 분석,
     * 고위험 유저 조회, 추가 입력 요청 중 하나를 결정합니다.
     *
     * LLM 장애 상황에서도 관리자 챗봇의 기본 흐름이 중단되지 않도록 하는
     * 장애 격리용 보조 로직입니다.
     */
    private AiReportChatIntentResult fallbackClassifyChatIntent(String message) {
        String normalized = message == null ? "" : message.toLowerCase();
        Long firstNumber = findFirstNumber(normalized);

        if (containsAny(normalized, "고위험", "위험 유저", "신고 많은", "신고많은", "블랙리스트", "후보")) {
            return new AiReportChatIntentResult(
                    AiReportChatAction.HIGH_RISK_USERS,
                    null,
                    firstNumber == null ? DEFAULT_HIGH_RISK_USER_LIMIT : firstNumber.intValue(),
                    null
            );
        }

        if (containsAny(normalized, "신고", "리포트", "report", "분석") && firstNumber != null) {
            return new AiReportChatIntentResult(
                    AiReportChatAction.ANALYZE_REPORT,
                    firstNumber,
                    null,
                    null
            );
        }

        return new AiReportChatIntentResult(
                AiReportChatAction.CLARIFY,
                null,
                null,
                "신고 ID를 지정해 분석을 요청하거나, 고위험 유저 조회를 요청해주세요."
        );
    }

    /**
     * AI 의도 분류 결과에서 실제 실행할 챗봇 액션을 결정합니다.
     *
     * 의도 분류 결과가 없거나 action 값이 비어 있으면 안전하게 CLARIFY로 처리하여
     * 잘못된 요청이 분석 로직으로 흘러가지 않도록 방어합니다.
     */
    private AiReportChatAction resolveChatAction(AiReportChatIntentResult intent) {
        return intent == null || intent.action() == null ? AiReportChatAction.CLARIFY : intent.action();
    }

    /**
     * 관리자 요청이 불명확할 때 추가 입력을 요청하는 응답을 생성합니다.
     *
     * 신고 ID가 없거나 의도 분류 결과가 CLARIFY인 경우 사용합니다.
     * 분석 결과나 고위험 유저 목록은 포함하지 않고, 사용자에게 다음 입력 방향만 안내합니다.
     */
    private AiReportChatResponseDto clarify(String message) {
        return new AiReportChatResponseDto(
                message,
                AiReportChatAction.CLARIFY,
                null,
                null,
                false
        );
    }

    /**
     * 신고 분석 결과를 관리자 챗봇 응답 문구로 변환합니다.
     *
     * AiReportAnalysisResponseDto에 담긴 처리 제안, 위험도, 요약,
     * 관리자 액션 가이드를 읽기 쉬운 자연어 형식으로 구성합니다.
     */
    private String buildAnalysisChatAnswer(AiReportAnalysisResponseDto analysis) {
        return """
                %d번 신고 분석이 완료되었습니다.
                처리 제안: %s
                위험도: %s
                요약: %s
                관리자 액션: %s
                """.formatted(
                analysis.reportId(),
                analysis.decisionSuggestion(),
                analysis.riskLevel(),
                analysis.summary(),
                analysis.actionGuide()
        );
    }

    /**
     * 고위험 유저 후보 조회 개수를 허용 범위 안으로 보정합니다.
     *
     * 사용자가 인원 수를 입력하지 않거나 1보다 작은 값을 입력하면 기본값을 사용하고,
     * 과도하게 큰 값은 최대 조회 개수로 제한하여 AI 호출 비용과 응답 크기를 제어합니다.
     */
    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_HIGH_RISK_USER_LIMIT;
        }

        return Math.min(limit, MAX_HIGH_RISK_USER_LIMIT);
    }

    /**
     * 자연어 메시지에서 처음 등장하는 숫자를 추출합니다.
     *
     * "12번 신고 분석해줘" 같은 요청에서는 신고 ID로 사용하고,
     * "고위험 유저 10명 보여줘" 같은 요청에서는 조회 제한 수로 사용합니다.
     */
    private Long findFirstNumber(String message) {
        Matcher matcher = NUMBER_PATTERN.matcher(message);
        if (!matcher.find()) {
            return null;
        }

        try {
            return Long.parseLong(matcher.group());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 메시지에 지정된 키워드 중 하나라도 포함되어 있는지 확인합니다.
     *
     * LLM 의도 분류가 실패했을 때 fallback 분류에서
     * 신고 분석 요청과 고위험 유저 조회 요청을 구분하기 위해 사용합니다.
     */
    private boolean containsAny(String message, String... keywords) {
        for (String keyword : keywords) {
            if (message.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 관리자 ID가 실제 관리자 계정인지 검증합니다.
     *
     * ai_report 도메인은 관리자 전용 기능이므로, 존재하지 않는 관리자 ID로 접근하면
     * 이후 AI 분석이나 Tool 호출이 실행되지 않도록 예외를 발생시킵니다.
     */
    private void validateAdmin(Long adminId) {
        adminService.validateAdmin(adminId);
    }

    /**
     * AI 신고 분석 실패 시 저장할 fallback 분석 결과를 생성합니다.
     *
     * LLM 호출, Tool 호출, 구조화 출력 파싱 중 문제가 발생해도
     * 관리자 화면에는 최소한의 검토 필요 상태를 남겨야 하므로
     * NEEDS_REVIEW 중심의 보수적인 분석 결과를 저장합니다.
     */
    private AiReportSummary saveFallbackSummary(
            String requestId,
            Long adminId,
            Long reportId,
            Long promptTemplateId,
            String promptVersion
    ) {
        Report report = reportService.getReportById(reportId);

        return aiReportSummaryRepository.save(
                AiReportSummary.builder()
                        .reportId(report.getId())
                        .adminId(adminId)
                        .requestId(requestId)
                        .reportReason(report.getReason())
                        .decisionSuggestion(AiReportDecisionSuggestion.NEEDS_REVIEW)
                        .riskLevel(AiReportRiskLevel.MEDIUM)
                        .summary("AI 신고 분석에 실패했습니다. 관리자의 직접 검토가 필요합니다.")
                        .evidence("AI 응답 생성 실패")
                        .recommendationReason("신고 원문과 대상 게시글을 직접 확인해야 합니다.")
                        .confidenceScore(0)
                        .needsAdminReview(true)
                        .fallbackUsed(true)
                        .model(aiProperties.getReport().getModel())
                        .promptTemplateId(promptTemplateId)
                        .promptVersion(promptVersion)
                        .build()
        );
    }

    /**
     * Tool 조회 결과를 AI 실패 시 사용할 고위험 유저 fallback DTO로 변환합니다.
     *
     * LLM이 고위험도 판단 문장을 생성하지 못하더라도,
     * 채택 신고 수와 미처리 신고 수를 기준으로 보수적인 위험도를 계산해
     * 관리자에게 검토 후보 목록을 제공합니다.
     */
    private AiReportHighRiskUserDto toFallbackHighRiskUser(AiReportHighRiskUserToolResult result) {
        AiReportRiskLevel riskLevel = result.acceptedReportCount() >= 3 || result.pendingReportCount() >= 5
                ? AiReportRiskLevel.HIGH
                : AiReportRiskLevel.MEDIUM;

        return new AiReportHighRiskUserDto(
                result.userId(),
                result.nickname(),
                riskLevel,
                result.totalReportCount(),
                result.pendingReportCount(),
                result.acceptedReportCount(),
                result.reasonSummary(),
                "관련 신고와 대상 게시글을 우선 검토한 뒤, 채택 누적 횟수에 따라 경고 또는 정지를 검토하세요.",
                result.relatedReportIds()
        );
    }

    /**
     * LLM이 반환한 처리 제안을 안전한 기본값으로 보정합니다.
     *
     * 구조화 출력이 비어 있거나 decisionSuggestion 값이 누락된 경우
     * AI가 임의로 채택/기각을 확정하지 않도록 NEEDS_REVIEW로 처리합니다.
     */
    private AiReportDecisionSuggestion resolveDecision(AiReportLlmResult result) {
        return result == null || result.decisionSuggestion() == null
                ? AiReportDecisionSuggestion.NEEDS_REVIEW
                : result.decisionSuggestion();
    }

    /**
     * LLM이 반환한 위험도를 안전한 기본값으로 보정합니다.
     *
     * 위험도 값이 누락된 경우 과도하게 낮거나 높은 판단을 피하기 위해
     * 관리자 추가 검토가 필요한 MEDIUM으로 처리합니다.
     */
    private AiReportRiskLevel resolveRiskLevel(AiReportLlmResult result) {
        return result == null || result.riskLevel() == null
                ? AiReportRiskLevel.MEDIUM
                : result.riskLevel();
    }

    /**
     * 관리자 추가 검토 필요 여부를 결정합니다.
     *
     * AI 응답이 없거나 needsAdminReview 값이 누락된 경우
     * 자동 판단을 신뢰하지 않고 관리자 검토가 필요한 상태로 보정합니다.
     */
    private boolean resolveNeedsReview(AiReportLlmResult result) {
        return result == null || result.needsAdminReview() == null || result.needsAdminReview();
    }

    /**
     * AI 신뢰도 점수를 0~100 범위로 보정합니다.
     *
     * LLM이 범위를 벗어난 값을 반환하거나 값을 누락해도
     * 저장 데이터와 화면 표시가 깨지지 않도록 정수 범위를 제한합니다.
     */
    private int resolveConfidence(Integer confidenceScore) {
        if (confidenceScore == null) {
            return 0;
        }

        return Math.max(0, Math.min(100, confidenceScore));
    }

    /**
     * 필수 텍스트 값이 비어 있을 때 사용할 기본 문구를 반환합니다.
     *
     * LLM 응답 중 summary, actionGuide처럼 화면에 반드시 필요한 문장이
     * null 또는 blank로 내려오는 경우를 방어합니다.
     */
    private String requiredText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * 처리 제안 enum에 맞는 관리자 액션 가이드를 생성합니다.
     *
     * AI의 판단은 최종 처분이 아니므로, 실제 채택/기각/추가 검토를
     * 관리자가 어떤 기준으로 이어가야 하는지 안내합니다.
     */
    private String buildActionGuide(AiReportDecisionSuggestion suggestion) {
        return switch (suggestion) {
            case ACCEPT -> "기존 관리자 신고 처리 API에서 ACCEPTED로 처리하고, 채택 누적 횟수에 따른 제재 정책을 확인하세요.";
            case REJECT -> "근거가 부족하면 기존 관리자 신고 처리 API에서 REJECTED로 처리하세요.";
            case NEEDS_REVIEW -> "채택 또는 기각 전 신고 원문, 대상 게시글, 누적 신고 이력을 추가 검토하세요.";
        };
    }

    /**
     * AI 처리 중 발생한 예외를 모니터링용 에러 유형으로 분류합니다.
     *
     * 프롬프트 로딩 실패는 PROMPT_LOAD_ERROR, Tool 계층 예외는 TOOL_ERROR,
     * 그 외 LLM 호출/파싱/서버 문제는 SERVER_ERROR로 기록합니다.
     */
    private AiErrorType resolveErrorType(Exception e) {
        if (e instanceof AiException) {
            return AiErrorType.PROMPT_LOAD_ERROR;
        }

        if (hasStackTraceClassContaining(e, ".domain.ai.report.tool.") || containsIgnoreCase(e.getMessage(), "tool")) {
            return AiErrorType.TOOL_ERROR;
        }

        return AiErrorType.SERVER_ERROR;
    }

    /**
     * 예외 stack trace에 특정 클래스 경로 키워드가 포함되어 있는지 확인합니다.
     *
     * Tool 호출 내부에서 발생한 예외가 여러 계층으로 감싸져 전달될 수 있으므로,
     * cause 체인 전체를 순회하며 Tool 계층 예외인지 판별합니다.
     */
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

    /**
     * 문자열에 특정 키워드가 대소문자 구분 없이 포함되어 있는지 확인합니다.
     *
     * 에러 메시지에 "tool" 같은 단서가 포함된 경우 TOOL_ERROR로 분류하기 위한
     * 보조 조건으로 사용합니다.
     */
    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword.toLowerCase());
    }

    /**
     * AI 호출 결과를 AiCallMetric으로 저장합니다.
     *
     * 요청 ID, 관리자 ID, 기능명, 모델명, 프롬프트 버전, 토큰 사용량,
     * 응답 지연 시간, 성공/fallback 상태, 에러 유형을 기록하여
     * 비용 추적과 장애 모니터링 대시보드에서 활용할 수 있게 합니다.
     */
    private void saveMetric(
            String requestId,
            Long adminId,
            long startedAt,
            AiCallStatus status,
            AiErrorType errorType,
            String errorMessage,
            Long promptTemplateId,
            String promptVersion,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens
    ) {
        // 신고 AI는 메트릭 저장 요청만 위임하고,
        // 실제 AiCallMetric Repository 접근은 ai.common 서비스가 담당합니다.
        // System.currentTimeMillis() - startedAt는 AI 호출에 걸린 시간(ms) 저장하는 코드.
        aiCallMetricService.createAiCallMetric(
                requestId,
                adminId,
                AiFeature.REPORT,
                aiProperties.getReport().getModel(),
                promptTemplateId,
                promptVersion,
                promptTokens,
                completionTokens,
                totalTokens,
                System.currentTimeMillis() - startedAt,
                status,
                errorType,
                errorMessage
        );
    }

    /**
     * Spring AI ChatResponse에서 토큰 사용량을 추출합니다.
     *
     * OpenAI 응답 메타데이터에 usage 정보가 포함된 경우
     * promptTokens, completionTokens, totalTokens를 AiCallMetric에 저장할 수 있도록 변환합니다.
     */
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

    /**
     * AI 호출 토큰 사용량을 내부에서 다루기 위한 값 객체입니다.
     *
     * usage 정보가 제공되지 않는 모델이나 fallback 흐름에서는
     * 세 필드를 null로 둔 empty 값을 사용합니다.
     */
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
     * DB 컬럼 길이에 맞게 문자열을 잘라냅니다.
     *
     * LLM 예외 메시지나 Tool 예외 메시지가 길어질 수 있으므로,
     * AiCallMetric.errorMessage 같은 제한된 컬럼에 저장하기 전에 길이를 제한합니다.
     */
    private String truncate(String message, int maxLength) {
        if (message == null) {
            return null;
        }

        return message.length() > maxLength ? message.substring(0, maxLength) : message;
    }
}
