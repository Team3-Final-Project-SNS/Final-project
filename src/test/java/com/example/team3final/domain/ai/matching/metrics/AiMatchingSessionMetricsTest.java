package com.example.team3final.domain.ai.matching.metrics;

import com.example.team3final.domain.ai.matching.repository.AiMatchingChatMemoryRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiMatchingSessionMetricsTest {

    @Mock
    private AiMatchingChatMemoryRepository aiMatchingChatMemoryRepository;

    private SimpleMeterRegistry meterRegistry;
    private AiMatchingSessionMetrics aiMatchingSessionMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        aiMatchingSessionMetrics = new AiMatchingSessionMetrics(meterRegistry, aiMatchingChatMemoryRepository);
        aiMatchingSessionMetrics.registerGauges();
    }

    @Test
    @DisplayName("활성 매칭 AI 세션 수를 Gauge로 노출한다")
    void activeSessionCount() {
        when(aiMatchingChatMemoryRepository.findActiveConversationTokenStats(any(LocalDateTime.class)))
                .thenReturn(List.of(stats(1L, "conversation-1", 1000L), stats(2L, "conversation-2", 2000L)));

        double value = meterRegistry.get("ai.matching.active.sessions").gauge().value();

        assertThat(value).isEqualTo(2.0);
    }

    @Test
    @DisplayName("활성 매칭 AI 세션별 평균 토큰 수를 Gauge로 노출한다")
    void averageSessionTokens() {
        when(aiMatchingChatMemoryRepository.findActiveConversationTokenStats(any(LocalDateTime.class)))
                .thenReturn(List.of(stats(1L, "conversation-1", 1000L), stats(2L, "conversation-2", 2000L)));

        double value = meterRegistry.get("ai.matching.session.tokens.avg").gauge().value();

        assertThat(value).isEqualTo(1500.0);
    }

    @Test
    @DisplayName("3000토큰 윈도우 대비 평균 사용률을 Gauge로 노출한다")
    void tokenWindowUsageRatio() {
        when(aiMatchingChatMemoryRepository.findActiveConversationTokenStats(any(LocalDateTime.class)))
                .thenReturn(List.of(stats(1L, "conversation-1", 1500L)));

        double value = meterRegistry.get("ai.matching.token.window.usage.ratio").gauge().value();

        assertThat(value).isEqualTo(0.5);
    }

    @Test
    @DisplayName("대화 세션별 누적 토큰 수를 conversationId 라벨로 노출한다")
    void sessionTokensByConversation() {
        when(aiMatchingChatMemoryRepository.findActiveConversationTokenStats(any(LocalDateTime.class)))
                .thenReturn(List.of(stats(1L, "conversation-1", 1200L), stats(2L, "conversation-2", 2800L)));

        aiMatchingSessionMetrics.refreshSessionTokenGauge();

        double firstConversationTokens = meterRegistry.get("ai.matching.session.tokens")
                .tag("conversation_id", "conversation-1")
                .gauge()
                .value();
        double secondConversationTokens = meterRegistry.get("ai.matching.session.tokens")
                .tag("conversation_id", "conversation-2")
                .gauge()
                .value();

        assertThat(firstConversationTokens).isEqualTo(1200.0);
        assertThat(secondConversationTokens).isEqualTo(2800.0);
    }

    private AiMatchingChatMemoryRepository.ActiveConversationTokenStats stats(
            Long userId,
            String conversationId,
            Long totalTokens
    ) {
        return new AiMatchingChatMemoryRepository.ActiveConversationTokenStats() {
            @Override
            public Long getUserId() {
                return userId;
            }

            @Override
            public String getConversationId() {
                return conversationId;
            }

            @Override
            public Long getTotalTokens() {
                return totalTokens;
            }
        };
    }
}
