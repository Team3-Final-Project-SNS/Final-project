package com.example.team3final.domain.ai.matching.service;

import com.example.team3final.common.config.AiProperties;
import com.example.team3final.domain.ai.common.service.AiCallMetricService;
import com.example.team3final.domain.ai.matching.repository.AiMatchingChatMemoryRepository;
import com.example.team3final.domain.ai.matching.repository.AiMatchingChatMessageRepository;
import com.example.team3final.domain.ai.matching.tool.AiMatchingTool;
import com.example.team3final.domain.ai.prompt.service.AiPromptFileService;
import com.example.team3final.domain.user.service.UserInternalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AI 매칭 서비스 단위 테스트")
class AiMatchingServiceTest {

    @Mock
    private AiPromptFileService aiPromptFileService;

    @Mock
    private AiMatchingTool aiMatchingTool;

    @Mock
    private AiCallMetricService aiCallMetricService;

    @Mock
    private AiProperties aiProperties;

    @Mock
    private UserInternalService userInternalService;

    @Mock
    private AiMatchingChatMemoryRepository aiMatchingChatMemoryRepository;

    @Mock
    private AiMatchingChatMessageRepository aiMatchingChatMessageRepository;

    @Test
    @DisplayName("만료된 AI 매칭 대화 세션을 메모리와 메시지 저장소에서 삭제한다")
    void cleanupExpiredMatchingMemory_shouldDeleteExpiredConversations() {
        ChatClient.Builder chatClientBuilder = mock(ChatClient.Builder.class);
        when(chatClientBuilder.build()).thenReturn(mock(ChatClient.class));
        AiMatchingChatMemoryRepository.ExpiredConversationKey key = mock(AiMatchingChatMemoryRepository.ExpiredConversationKey.class);
        when(key.getUserId()).thenReturn(1L);
        when(key.getConversationId()).thenReturn("conversation-1");
        when(aiMatchingChatMemoryRepository.findExpiredConversationKeys(any())).thenReturn(List.of(key));
        AiMatchingServiceImpl service = new AiMatchingServiceImpl(
                chatClientBuilder,
                aiPromptFileService,
                aiMatchingTool,
                aiCallMetricService,
                aiProperties,
                userInternalService,
                aiMatchingChatMemoryRepository,
                aiMatchingChatMessageRepository
        );

        service.cleanupExpiredMatchingMemory();

        verify(aiMatchingChatMemoryRepository).deleteByUserIdAndConversationId(1L, "conversation-1");
        verify(aiMatchingChatMessageRepository).deleteByUserIdAndConversationId(1L, "conversation-1");
    }
}
