package com.example.team3final.domain.ai.common.service;

import com.example.team3final.domain.ai.common.enums.AiCallStatus;
import com.example.team3final.domain.ai.common.enums.AiErrorType;
import com.example.team3final.domain.ai.common.enums.AiFeature;

public interface AiCallMetricService {

    /**
     * AI 호출 결과 메트릭을 저장합니다.
     *
     * 각 AI 기능 도메인은 Repository를 직접 참조하지 않고,
     * 공통 메트릭 도메인 서비스를 통해 호출 상태와 토큰 사용량을 기록합니다.
     */
    void createAiCallMetric(
            String requestId,
            Long userId,
            AiFeature feature,
            String model,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            Long latencyMs,
            AiCallStatus status,
            AiErrorType errorType,
            String errorMessage
    );
}
