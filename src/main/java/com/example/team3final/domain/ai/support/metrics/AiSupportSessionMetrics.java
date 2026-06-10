package com.example.team3final.domain.ai.support.metrics;

import com.example.team3final.domain.ai.support.repository.AiSupportChatMemoryRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AiSupportSessionMetrics {

    private static final int SUPPORT_MEMORY_TOKEN_BUDGET = 3000;
    private static final int SUPPORT_SESSION_EXPIRE_MINUTES = 15;

    private final MeterRegistry meterRegistry;
    private final AiSupportChatMemoryRepository aiSupportChatMemoryRepository;

    private MultiGauge sessionTokensGauge;

    @PostConstruct
    public void registerGauges() {
        Gauge.builder("ai.support.session.tokens.avg", this, AiSupportSessionMetrics::averageSessionTokens)
                .description("Average token count per active Support AI conversation")
                .register(meterRegistry);

        Gauge.builder("ai.support.token.window.usage.ratio", this, AiSupportSessionMetrics::tokenWindowUsageRatio)
                .description("Average active Support AI conversation token usage ratio against the token window")
                .register(meterRegistry);

        sessionTokensGauge = MultiGauge.builder("ai.support.session.tokens")
                .description("Token count per active Support AI conversation")
                .register(meterRegistry);

        refreshSessionTokenGauge();
    }

    @Scheduled(fixedDelay = 30000)
    void refreshSessionTokenGauge() {
        if (sessionTokensGauge == null) {
            return;
        }

        List<MultiGauge.Row<Number>> rows = activeConversationStats().stream()
                .map(stats -> MultiGauge.Row.of(
                        Tags.of("conversation_id", conversationId(stats)),
                        totalTokens(stats)
                ))
                .toList();

        sessionTokensGauge.register(rows, true);
    }

    double averageSessionTokens() {
        List<AiSupportChatMemoryRepository.ActiveConversationTokenStats> stats = activeConversationStats();
        if (stats.isEmpty()) {
            return 0.0;
        }

        return stats.stream()
                .mapToLong(this::totalTokens)
                .average()
                .orElse(0.0);
    }

    double tokenWindowUsageRatio() {
        return averageSessionTokens() / SUPPORT_MEMORY_TOKEN_BUDGET;
    }

    private List<AiSupportChatMemoryRepository.ActiveConversationTokenStats> activeConversationStats() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(SUPPORT_SESSION_EXPIRE_MINUTES);
        List<AiSupportChatMemoryRepository.ActiveConversationTokenStats> stats =
                aiSupportChatMemoryRepository.findActiveConversationTokenStats(cutoff);
        return stats == null ? List.of() : stats;
    }

    private long totalTokens(AiSupportChatMemoryRepository.ActiveConversationTokenStats stats) {
        return stats.getTotalTokens() == null ? 0L : stats.getTotalTokens();
    }

    private String conversationId(AiSupportChatMemoryRepository.ActiveConversationTokenStats stats) {
        return stats.getConversationId() == null ? "unknown" : stats.getConversationId();
    }
}
