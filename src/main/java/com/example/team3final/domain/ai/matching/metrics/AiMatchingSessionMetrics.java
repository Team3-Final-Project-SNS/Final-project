package com.example.team3final.domain.ai.matching.metrics;

import com.example.team3final.domain.ai.matching.repository.AiMatchingChatMemoryRepository;
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
public class AiMatchingSessionMetrics {

    private static final int MATCHING_MEMORY_TOKEN_BUDGET = 3000;
    private static final int MATCHING_SESSION_EXPIRE_MINUTES = 15;

    private final MeterRegistry meterRegistry;
    private final AiMatchingChatMemoryRepository aiMatchingChatMemoryRepository;

    private MultiGauge sessionTokensGauge;

    @PostConstruct
    public void registerGauges() {
        Gauge.builder("ai.matching.active.sessions", this, AiMatchingSessionMetrics::activeSessionCount)
                .description("Active Matching AI conversation count")
                .register(meterRegistry);

        Gauge.builder("ai.matching.session.tokens.avg", this, AiMatchingSessionMetrics::averageSessionTokens)
                .description("Average token count per active Matching AI conversation")
                .register(meterRegistry);

        Gauge.builder("ai.matching.token.window.usage.ratio", this, AiMatchingSessionMetrics::tokenWindowUsageRatio)
                .description("Average active Matching AI conversation token usage ratio against the token window")
                .register(meterRegistry);

        sessionTokensGauge = MultiGauge.builder("ai.matching.session.tokens")
                .description("Token count per active Matching AI conversation")
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

    double activeSessionCount() {
        return activeConversationStats().size();
    }

    double averageSessionTokens() {
        List<AiMatchingChatMemoryRepository.ActiveConversationTokenStats> stats = activeConversationStats();
        if (stats.isEmpty()) {
            return 0.0;
        }

        return stats.stream()
                .mapToLong(this::totalTokens)
                .average()
                .orElse(0.0);
    }

    double tokenWindowUsageRatio() {
        return averageSessionTokens() / MATCHING_MEMORY_TOKEN_BUDGET;
    }

    private List<AiMatchingChatMemoryRepository.ActiveConversationTokenStats> activeConversationStats() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(MATCHING_SESSION_EXPIRE_MINUTES);
        List<AiMatchingChatMemoryRepository.ActiveConversationTokenStats> stats =
                aiMatchingChatMemoryRepository.findActiveConversationTokenStats(cutoff);
        return stats == null ? List.of() : stats;
    }

    private long totalTokens(AiMatchingChatMemoryRepository.ActiveConversationTokenStats stats) {
        return stats.getTotalTokens() == null ? 0L : stats.getTotalTokens();
    }

    private String conversationId(AiMatchingChatMemoryRepository.ActiveConversationTokenStats stats) {
        return stats.getConversationId() == null ? "unknown" : stats.getConversationId();
    }
}
