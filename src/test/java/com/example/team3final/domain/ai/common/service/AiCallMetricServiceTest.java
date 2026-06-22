package com.example.team3final.domain.ai.common.service;

import com.example.team3final.domain.ai.common.entity.AiCallMetric;
import com.example.team3final.domain.ai.common.enums.AiCallStatus;
import com.example.team3final.domain.ai.common.enums.AiErrorType;
import com.example.team3final.domain.ai.common.enums.AiFeature;
import com.example.team3final.domain.ai.common.repository.AiCallMetricRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AI 호출 메트릭 서비스 단위 테스트")
class AiCallMetricServiceTest {

    @Mock
    private AiCallMetricRepository aiCallMetricRepository;

    @Test
    @DisplayName("AI 호출 메트릭을 저장하고 긴 오류 메시지는 저장 길이에 맞게 자른다")
    void createAiCallMetric_shouldSaveMetricWithTruncatedErrorMessage() {
        AiCallMetricServiceImpl service = new AiCallMetricServiceImpl(aiCallMetricRepository, new SimpleMeterRegistry());
        String longErrorMessage = "x".repeat(600);

        service.createAiCallMetric(
                "request-1",
                1L,
                AiFeature.MATCHING,
                "gpt-test",
                10L,
                "v1",
                10,
                20,
                30,
                100L,
                AiCallStatus.FAILED,
                AiErrorType.SERVER_ERROR,
                longErrorMessage
        );

        ArgumentCaptor<AiCallMetric> captor = ArgumentCaptor.forClass(AiCallMetric.class);
        verify(aiCallMetricRepository).save(captor.capture());
        assertThat(captor.getValue().getRequestId()).isEqualTo("request-1");
        assertThat(captor.getValue().getErrorMessage()).hasSize(500);
    }
}
