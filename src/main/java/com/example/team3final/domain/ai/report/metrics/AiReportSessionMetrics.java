package com.example.team3final.domain.ai.report.metrics;

import com.example.team3final.domain.ai.report.repository.AiReportChatMemoryRepository;
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
public class AiReportSessionMetrics {

    private static final int REPORT_MEMORY_TOKEN_BUDGET = 6000;
    private static final int REPORT_SESSION_EXPIRE_MINUTES = 15;

    private final MeterRegistry meterRegistry;
    private final AiReportChatMemoryRepository aiReportChatMemoryRepository;

    private MultiGauge sessionTokensGauge;

    @PostConstruct
    public void registerGauges() {
        Gauge.builder("ai.report.session.tokens.avg", this, AiReportSessionMetrics::averageSessionTokens)
                .description("Average token count per active Report AI conversation")
                .register(meterRegistry);

        Gauge.builder("ai.report.token.window.usage.ratio", this, AiReportSessionMetrics::tokenWindowUsageRatio)
                .description("Average active Report AI conversation token usage ratio against the token window")
                .register(meterRegistry);

        sessionTokensGauge = MultiGauge.builder("ai.report.session.tokens")
                .description("Token count per active Report AI conversation")
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
        List<AiReportChatMemoryRepository.ActiveConversationTokenStats> stats = activeConversationStats();
        if (stats.isEmpty()) {
            return 0.0;
        }

        return stats.stream()
                .mapToLong(this::totalTokens)
                .average()
                .orElse(0.0);
    }

    double tokenWindowUsageRatio() {
        return averageSessionTokens() / REPORT_MEMORY_TOKEN_BUDGET;
    }

    private List<AiReportChatMemoryRepository.ActiveConversationTokenStats> activeConversationStats() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(REPORT_SESSION_EXPIRE_MINUTES);
        List<AiReportChatMemoryRepository.ActiveConversationTokenStats> stats =
                aiReportChatMemoryRepository.findActiveConversationTokenStats(cutoff);
        return stats == null ? List.of() : stats;
    }

    private long totalTokens(AiReportChatMemoryRepository.ActiveConversationTokenStats stats) {
        return stats.getTotalTokens() == null ? 0L : stats.getTotalTokens();
    }

    private String conversationId(AiReportChatMemoryRepository.ActiveConversationTokenStats stats) {
        return stats.getConversationId() == null ? "unknown" : stats.getConversationId();
    }
}
