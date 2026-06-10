package com.example.team3final.domain.ai.common.service;

import com.example.team3final.domain.ai.common.entity.AiCallMetric;
import com.example.team3final.domain.ai.common.enums.AiCallStatus;
import com.example.team3final.domain.ai.common.enums.AiErrorType;
import com.example.team3final.domain.ai.common.enums.AiFeature;
import com.example.team3final.domain.ai.common.repository.AiCallMetricRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AiCallMetricServiceImplTest {

    @Mock
    private AiCallMetricRepository aiCallMetricRepository;

    @InjectMocks
    private AiCallMetricServiceImpl aiCallMetricService;

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
    }
}
