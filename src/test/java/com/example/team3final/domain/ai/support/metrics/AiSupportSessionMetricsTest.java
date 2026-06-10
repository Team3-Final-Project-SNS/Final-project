package com.example.team3final.domain.ai.support.metrics;

import com.example.team3final.domain.ai.support.repository.AiSupportChatMemoryRepository;
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
class AiSupportSessionMetricsTest {

    @Mock
    private AiSupportChatMemoryRepository aiSupportChatMemoryRepository;

    private SimpleMeterRegistry meterRegistry;
    private AiSupportSessionMetrics aiSupportSessionMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        aiSupportSessionMetrics = new AiSupportSessionMetrics(meterRegistry, aiSupportChatMemoryRepository);
        aiSupportSessionMetrics.registerGauges();
    }

    @Test
    @DisplayName("활성 고객센터 AI 세션별 평균 토큰 수를 Gauge로 노출한다")
    void averageSessionTokens() {
        when(aiSupportChatMemoryRepository.findActiveConversationTokenStats(any(LocalDateTime.class)))
                .thenReturn(List.of(stats(1L, "conversation-1", 300L), stats(2L, "conversation-2", 900L)));

        double value = meterRegistry.get("ai.support.session.tokens.avg").gauge().value();

        assertThat(value).isEqualTo(600.0);
    }

    @Test
    @DisplayName("3000토큰 윈도우 대비 고객센터 AI 평균 사용률을 Gauge로 노출한다")
    void tokenWindowUsageRatio() {
        when(aiSupportChatMemoryRepository.findActiveConversationTokenStats(any(LocalDateTime.class)))
                .thenReturn(List.of(stats(1L, "conversation-1", 1500L)));

        double value = meterRegistry.get("ai.support.token.window.usage.ratio").gauge().value();

        assertThat(value).isEqualTo(0.5);
    }

    @Test
    @DisplayName("고객센터 AI 대화 세션별 누적 토큰 수를 conversationId 라벨로 노출한다")
    void sessionTokensByConversation() {
        when(aiSupportChatMemoryRepository.findActiveConversationTokenStats(any(LocalDateTime.class)))
                .thenReturn(List.of(stats(1L, "conversation-1", 120L), stats(2L, "conversation-2", 480L)));

        aiSupportSessionMetrics.refreshSessionTokenGauge();

        double firstConversationTokens = meterRegistry.get("ai.support.session.tokens")
                .tag("conversation_id", "conversation-1")
                .gauge()
                .value();
        double secondConversationTokens = meterRegistry.get("ai.support.session.tokens")
                .tag("conversation_id", "conversation-2")
                .gauge()
                .value();

        assertThat(firstConversationTokens).isEqualTo(120.0);
        assertThat(secondConversationTokens).isEqualTo(480.0);
    }

    private AiSupportChatMemoryRepository.ActiveConversationTokenStats stats(
            Long userId,
            String conversationId,
            Long totalTokens
    ) {
        return new AiSupportChatMemoryRepository.ActiveConversationTokenStats() {
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
