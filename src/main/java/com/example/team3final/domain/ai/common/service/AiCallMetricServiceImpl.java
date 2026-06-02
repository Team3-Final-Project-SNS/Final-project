package com.example.team3final.domain.ai.common.service;

import com.example.team3final.domain.ai.common.entity.AiCallMetric;
import com.example.team3final.domain.ai.common.enums.AiCallStatus;
import com.example.team3final.domain.ai.common.enums.AiErrorType;
import com.example.team3final.domain.ai.common.enums.AiFeature;
import com.example.team3final.domain.ai.common.repository.AiCallMetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiCallMetricServiceImpl implements AiCallMetricService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    private final AiCallMetricRepository aiCallMetricRepository;

    /**
     * AI 호출 결과를 AiCallMetric으로 저장합니다.
     *
     * 매칭/신고/고객센터처럼 여러 AI 기능 도메인에서 공통으로 필요한
     * 요청 ID, 기능명, 모델명, 토큰 사용량, 지연 시간, 성공/실패 상태를 기록합니다.
     * 각 기능 서비스가 Repository를 직접 다루지 않도록 메트릭 저장 책임을 이 서비스로 모읍니다.
     */
    @Override
    public void createAiCallMetric(
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
    ) {
        // AI 기능 도메인별 메트릭 저장 규칙을 한 곳에 모읍니다.
        // 호출 서비스는 상태와 수치만 전달하고, Repository 접근은 공통 서비스가 담당합니다.
        aiCallMetricRepository.save(
                AiCallMetric.builder()
                        .requestId(requestId)
                        .userId(userId)
                        .feature(feature)
                        .model(model)
                        .promptTokens(promptTokens)
                        .completionTokens(completionTokens)
                        .totalTokens(totalTokens)
                        .latencyMs(latencyMs)
                        .status(status)
                        .errorType(errorType)
                        .errorMessage(truncate(errorMessage))
                        .build()
        );
    }

    /**
     * DB 컬럼 길이를 넘을 수 있는 에러 메시지를 저장 가능한 길이로 제한합니다.
     *
     * 외부 AI API 오류나 Tool 예외 메시지는 길이가 일정하지 않기 때문에,
     * 저장 시점에 공통으로 자르면 각 AI 기능 서비스가 같은 방어 로직을 반복하지 않아도 됩니다.
     */
    private String truncate(String message) {
        if (message == null) {
            return null;
        }

        return message.length() > MAX_ERROR_MESSAGE_LENGTH
                ? message.substring(0, MAX_ERROR_MESSAGE_LENGTH)
                : message;
    }
}
