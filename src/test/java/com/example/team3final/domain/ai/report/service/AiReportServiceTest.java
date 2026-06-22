package com.example.team3final.domain.ai.report.service;

import com.example.team3final.common.config.AiProperties;
import com.example.team3final.domain.admin.service.AdminService;
import com.example.team3final.domain.ai.common.service.AiCallMetricService;
import com.example.team3final.domain.ai.prompt.service.AiPromptFileService;
import com.example.team3final.domain.ai.rag.service.AiRagRetrieverService;
import com.example.team3final.domain.ai.report.repository.AiAdminResultRepository;
import com.example.team3final.domain.ai.report.repository.AiReportChatMemoryRepository;
import com.example.team3final.domain.ai.report.tool.AiReportTool;
import com.example.team3final.domain.report.service.ReportInternalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AI 신고 분석 서비스 단위 테스트")
class AiReportServiceTest {

    @Mock
    private AiPromptFileService aiPromptFileService;

    @Mock
    private AiReportTool aiReportTool;

    @Mock
    private AiReportChatMemoryRepository aiReportChatMemoryRepository;

    @Mock
    private AiAdminResultRepository aiAdminResultRepository;

    @Mock
    private AiCallMetricService aiCallMetricService;

    @Mock
    private AiProperties aiProperties;

    @Mock
    private AdminService adminService;

    @Mock
    private ReportInternalService reportInternalService;

    @Test
    @DisplayName("만료된 AI 신고 분석 대화 세션을 메모리 저장소에서 삭제한다")
    void cleanupExpiredReportSessions_shouldDeleteExpiredConversations() {
        ChatClient.Builder chatClientBuilder = mock(ChatClient.Builder.class);
        when(chatClientBuilder.build()).thenReturn(mock(ChatClient.class));
        ObjectProvider<AiRagRetrieverService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        AiReportChatMemoryRepository.ExpiredConversationKey key = mock(AiReportChatMemoryRepository.ExpiredConversationKey.class);
        when(key.getAdminId()).thenReturn(1L);
        when(key.getConversationId()).thenReturn("conversation-1");
        when(aiReportChatMemoryRepository.findExpiredConversationKeys(any())).thenReturn(List.of(key));
        AiReportServiceImpl service = new AiReportServiceImpl(
                chatClientBuilder,
                aiPromptFileService,
                aiReportTool,
                aiReportChatMemoryRepository,
                aiAdminResultRepository,
                aiCallMetricService,
                aiProperties,
                adminService,
                reportInternalService,
                provider
        );

        service.cleanupExpiredReportSessions();

        verify(aiReportChatMemoryRepository).deleteByAdminIdAndConversationId(1L, "conversation-1");
    }
}
