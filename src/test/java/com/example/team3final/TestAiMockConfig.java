package com.example.team3final;

import com.example.team3final.domain.ai.matching.service.AiMatchingService;
import com.example.team3final.domain.ai.report.dashboard.service.AiReportDashboardQueryService;
import com.example.team3final.domain.ai.report.service.AiReportService;
import com.example.team3final.domain.ai.support.service.AiSupportService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import static org.mockito.Mockito.mock;

@Configuration
@Profile("test")
public class TestAiMockConfig {

    @Bean
    @Primary
    public AiMatchingService aiMatchingServiceImpl() {
        return mock(AiMatchingService.class);
    }

    @Bean
    public AiSupportService aiSupportServiceImpl() {
        return mock(AiSupportService.class);
    }

    @Bean
    public AiReportService aiReportServiceImpl() {
        return mock(AiReportService.class);
    }

    @Bean
    public AiReportDashboardQueryService aiReportDashboardQueryServiceImpl() {
        return mock(AiReportDashboardQueryService.class);
    }
}
