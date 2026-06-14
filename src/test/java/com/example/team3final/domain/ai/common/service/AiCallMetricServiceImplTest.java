package com.example.team3final.domain.ai.common.service;

import com.example.team3final.domain.ai.common.entity.AiCallMetric;
import com.example.team3final.domain.ai.common.enums.AiCallStatus;
import com.example.team3final.domain.ai.common.enums.AiErrorType;
import com.example.team3final.domain.ai.common.enums.AiFeature;
import com.example.team3final.domain.ai.common.repository.AiCallMetricRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AiCallMetricServiceImplTest {

    @Mock
    private AiCallMetricRepository aiCallMetricRepository;

    private SimpleMeterRegistry meterRegistry;

    private AiCallMetricServiceImpl aiCallMetricService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        aiCallMetricService = new AiCallMetricServiceImpl(aiCallMetricRepository, meterRegistry);
    }

    @Test
    @DisplayName("AI 호출 성공 시 토큰 사용량과 응답 시간을 SUCCESS 메트릭으로 저장한다")
    void createAiCallMetric_success() {
        aiCallMetricService.createAiCallMetric(
                "request-success-1",
                1L,
                AiFeature.MATCHING,
                "gpt-4o-mini",
                10L,
                "v1",
                120,
                80,
                200,
                1500L,
                AiCallStatus.SUCCESS,
                null,
                null
        );

        ArgumentCaptor<AiCallMetric> captor = ArgumentCaptor.forClass(AiCallMetric.class);
        verify(aiCallMetricRepository).save(captor.capture());

        AiCallMetric metric = captor.getValue();
        assertThat(metric.getRequestId()).isEqualTo("request-success-1");
        assertThat(metric.getUserId()).isEqualTo(1L);
        assertThat(metric.getFeature()).isEqualTo(AiFeature.MATCHING);
        assertThat(metric.getModel()).isEqualTo("gpt-4o-mini");
        assertThat(metric.getPromptTemplateId()).isEqualTo(10L);
        assertThat(metric.getPromptVersion()).isEqualTo("v1");
        assertThat(metric.getPromptTokens()).isEqualTo(120);
        assertThat(metric.getCompletionTokens()).isEqualTo(80);
        assertThat(metric.getTotalTokens()).isEqualTo(200);
        assertThat(metric.getLatencyMs()).isEqualTo(1500L);
        assertThat(metric.getStatus()).isEqualTo(AiCallStatus.SUCCESS);
        assertThat(metric.getErrorType()).isNull();
        assertThat(metric.getErrorMessage()).isNull();

        assertThat(meterRegistry.get("ai.matching.call")
                .tag("model", "gpt-4o-mini")
                .tag("status", "SUCCESS")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("ai.matching.tokens").counter().count()).isEqualTo(200.0);
        assertThat(meterRegistry.get("ai.matching.prompt.tokens").counter().count()).isEqualTo(120.0);
        assertThat(meterRegistry.get("ai.matching.completion.tokens").counter().count()).isEqualTo(80.0);
        assertThat(meterRegistry.get("ai.matching.latency.ms").summary().totalAmount()).isEqualTo(1500.0);
    }

    @Test
    @DisplayName("LLM 장애로 Fallback 응답을 사용하면 오류 유형과 메시지를 FALLBACK 메트릭으로 저장한다")
    void createAiCallMetric_fallback() {
        aiCallMetricService.createAiCallMetric(
                "request-fallback-1",
                2L,
                AiFeature.MATCHING,
                "gpt-4o-mini",
                11L,
                "v2",
                90,
                20,
                110,
                3200L,
                AiCallStatus.FALLBACK,
                AiErrorType.SERVER_ERROR,
                "OpenAI API 5xx"
        );

        ArgumentCaptor<AiCallMetric> captor = ArgumentCaptor.forClass(AiCallMetric.class);
        verify(aiCallMetricRepository).save(captor.capture());

        AiCallMetric metric = captor.getValue();
        assertThat(metric.getRequestId()).isEqualTo("request-fallback-1");
        assertThat(metric.getFeature()).isEqualTo(AiFeature.MATCHING);
        assertThat(metric.getStatus()).isEqualTo(AiCallStatus.FALLBACK);
        assertThat(metric.getErrorType()).isEqualTo(AiErrorType.SERVER_ERROR);
        assertThat(metric.getErrorMessage()).isEqualTo("OpenAI API 5xx");
        assertThat(metric.getPromptTokens()).isEqualTo(90);
        assertThat(metric.getCompletionTokens()).isEqualTo(20);
        assertThat(metric.getTotalTokens()).isEqualTo(110);
        assertThat(metric.getLatencyMs()).isEqualTo(3200L);

        assertThat(meterRegistry.get("ai.matching.call")
                .tag("model", "gpt-4o-mini")
                .tag("status", "FALLBACK")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("ai.matching.error")
                .tag("error_type", "SERVER_ERROR")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("ai.matching.tokens").counter().count()).isEqualTo(110.0);
        assertThat(meterRegistry.get("ai.matching.latency.ms").summary().totalAmount()).isEqualTo(3200.0);
    }

    @Test
    @DisplayName("장애 메시지가 길어도 저장 가능한 500자로 잘라서 FAILED 메트릭으로 저장한다")
    void createAiCallMetric_truncatesLongErrorMessage() {
        String longErrorMessage = "x".repeat(600);

        aiCallMetricService.createAiCallMetric(
                "request-failed-1",
                3L,
                AiFeature.MATCHING,
                "gpt-4o-mini",
                12L,
                "v3",
                null,
                null,
                null,
                5000L,
                AiCallStatus.FAILED,
                AiErrorType.RATE_LIMIT,
                longErrorMessage
        );

        ArgumentCaptor<AiCallMetric> captor = ArgumentCaptor.forClass(AiCallMetric.class);
        verify(aiCallMetricRepository).save(captor.capture());

        AiCallMetric metric = captor.getValue();
        assertThat(metric.getRequestId()).isEqualTo("request-failed-1");
        assertThat(metric.getStatus()).isEqualTo(AiCallStatus.FAILED);
        assertThat(metric.getErrorType()).isEqualTo(AiErrorType.RATE_LIMIT);
        assertThat(metric.getErrorMessage()).hasSize(500);
        assertThat(metric.getErrorMessage()).isEqualTo("x".repeat(500));
        assertThat(metric.getLatencyMs()).isEqualTo(5000L);

        assertThat(meterRegistry.get("ai.matching.call")
                .tag("model", "gpt-4o-mini")
                .tag("status", "FAILED")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("ai.matching.error")
                .tag("error_type", "RATE_LIMIT")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.find("ai.matching.tokens").counter()).isNull();
        assertThat(meterRegistry.get("ai.matching.latency.ms").summary().totalAmount()).isEqualTo(5000.0);
    }

    @Test
    @DisplayName("고객센터 AI 호출 메트릭은 SUPPORT 전용 Prometheus 메트릭으로 기록한다")
    void createAiCallMetric_supportPrometheusMetrics() {
        aiCallMetricService.createAiCallMetric(
                "request-support-1",
                4L,
                AiFeature.SUPPORT,
                "gpt-4o-mini",
                13L,
                "v1",
                180,
                70,
                250,
                2400L,
                AiCallStatus.SUCCESS,
                null,
                null
        );

        ArgumentCaptor<AiCallMetric> captor = ArgumentCaptor.forClass(AiCallMetric.class);
        verify(aiCallMetricRepository).save(captor.capture());

        AiCallMetric metric = captor.getValue();
        assertThat(metric.getFeature()).isEqualTo(AiFeature.SUPPORT);
        assertThat(metric.getStatus()).isEqualTo(AiCallStatus.SUCCESS);

        assertThat(meterRegistry.get("ai.support.call")
                .tag("model", "gpt-4o-mini")
                .tag("status", "SUCCESS")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("ai.support.tokens").counter().count()).isEqualTo(250.0);
        assertThat(meterRegistry.get("ai.support.prompt.tokens").counter().count()).isEqualTo(180.0);
        assertThat(meterRegistry.get("ai.support.completion.tokens").counter().count()).isEqualTo(70.0);
        assertThat(meterRegistry.get("ai.support.latency.ms").summary().totalAmount()).isEqualTo(2400.0);
    }

    @Test
    @DisplayName("관리자 AI 호출 메트릭은 REPORT 전용 Prometheus 메트릭으로 기록한다")
    void createAiCallMetric_reportPrometheusMetrics() {
        aiCallMetricService.createAiCallMetric(
                "request-report-1",
                5L,
                AiFeature.REPORT,
                "gpt-4o-mini",
                14L,
                "v2",
                220,
                90,
                310,
                2800L,
                AiCallStatus.SUCCESS,
                null,
                null
        );

        ArgumentCaptor<AiCallMetric> captor = ArgumentCaptor.forClass(AiCallMetric.class);
        verify(aiCallMetricRepository).save(captor.capture());

        AiCallMetric metric = captor.getValue();
        assertThat(metric.getRequestId()).isEqualTo("request-report-1");
        assertThat(metric.getUserId()).isEqualTo(5L);
        assertThat(metric.getFeature()).isEqualTo(AiFeature.REPORT);
        assertThat(metric.getModel()).isEqualTo("gpt-4o-mini");
        assertThat(metric.getPromptTemplateId()).isEqualTo(14L);
        assertThat(metric.getPromptVersion()).isEqualTo("v2");
        assertThat(metric.getPromptTokens()).isEqualTo(220);
        assertThat(metric.getCompletionTokens()).isEqualTo(90);
        assertThat(metric.getTotalTokens()).isEqualTo(310);
        assertThat(metric.getLatencyMs()).isEqualTo(2800L);
        assertThat(metric.getStatus()).isEqualTo(AiCallStatus.SUCCESS);
        assertThat(metric.getErrorType()).isNull();
        assertThat(metric.getErrorMessage()).isNull();

        assertThat(meterRegistry.get("ai.report.call")
                .tag("model", "gpt-4o-mini")
                .tag("status", "SUCCESS")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("ai.report.tokens").counter().count()).isEqualTo(310.0);
        assertThat(meterRegistry.get("ai.report.prompt.tokens").counter().count()).isEqualTo(220.0);
        assertThat(meterRegistry.get("ai.report.completion.tokens").counter().count()).isEqualTo(90.0);
        assertThat(meterRegistry.get("ai.report.latency.ms").summary().totalAmount()).isEqualTo(2800.0);
    }
}
