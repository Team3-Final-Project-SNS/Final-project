package com.example.team3final.domain.ai.support.service;

import com.example.team3final.common.config.AiProperties;
import com.example.team3final.domain.ai.common.service.AiCallMetricService;
import com.example.team3final.domain.ai.prompt.service.AiPromptFileService;
import com.example.team3final.domain.ai.rag.service.AiRagRetrieverService;
import com.example.team3final.domain.ai.support.repository.AiSupportChatMemoryRepository;
import com.example.team3final.domain.ai.support.repository.AiSupportChatMessageRepository;
import com.example.team3final.domain.ai.support.tool.AiSupportTool;
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
@DisplayName("AI 고객센터 서비스 단위 테스트")
class AiSupportServiceTest {

    @Mock
    private AiPromptFileService aiPromptFileService;

    @Mock
    private AiSupportTool aiSupportTool;

    @Mock
    private AiSupportChatMessageRepository aiSupportChatMessageRepository;

    @Mock
    private AiSupportChatMemoryRepository aiSupportChatMemoryRepository;

    @Mock
    private AiCallMetricService aiCallMetricService;

    @Mock
    private AiProperties aiProperties;

    @Test
    @DisplayName("만료된 AI 고객센터 대화 세션을 메모리와 메시지 저장소에서 삭제한다")
    void cleanupExpiredSupportSessions_shouldDeleteExpiredConversations() {
        ChatClient.Builder chatClientBuilder = mock(ChatClient.Builder.class);
        when(chatClientBuilder.build()).thenReturn(mock(ChatClient.class));
        ObjectProvider<AiRagRetrieverService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        AiSupportChatMemoryRepository.ExpiredConversationKey key = mock(AiSupportChatMemoryRepository.ExpiredConversationKey.class);
        when(key.getUserId()).thenReturn(1L);
        when(key.getConversationId()).thenReturn("conversation-1");
        when(aiSupportChatMemoryRepository.findExpiredConversationKeys(any())).thenReturn(List.of(key));
        AiSupportServiceImpl service = new AiSupportServiceImpl(
                chatClientBuilder,
                aiPromptFileService,
                aiSupportTool,
                aiSupportChatMessageRepository,
                aiSupportChatMemoryRepository,
                aiCallMetricService,
                aiProperties,
                provider
        );

        service.cleanupExpiredSupportSessions();

        verify(aiSupportChatMemoryRepository).deleteByUserIdAndConversationId(1L, "conversation-1");
        verify(aiSupportChatMessageRepository).deleteByUserIdAndConversationId(1L, "conversation-1");
    }
}
