package com.example.team3final.domain.ai.matching.controller;

import com.example.team3final.domain.ai.matching.dto.request.AiMatchingChatRequestDto;
import com.example.team3final.domain.ai.matching.service.AiMatchingService;
import com.example.team3final.test.controller.ControllerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Flux;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("AI 매칭 컨트롤러 통합 테스트")
class AiMatchingControllerTest extends ControllerTestSupport {

    @Mock
    private AiMatchingService aiMatchingService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvcFor(new AiMatchingController(aiMatchingService));
    }

    @Test
    @DisplayName("AI 매칭 스트림 API는 인증 이메일과 요청 본문을 서비스로 전달한다")
    void streamChat_shouldDelegateToService() throws Exception {
        when(aiMatchingService.streamChat(eq("user1@test.ac.kr"), any(AiMatchingChatRequestDto.class)))
                .thenReturn(Flux.just("추천 응답"));

        mockMvc.perform(post("/api/v1/ai/matching/chat/stream")
                        .principal(userAuthentication(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": "conversation-1",
                                  "message": "조용한 행사를 추천해줘"
                                }
                                """))
                .andExpect(status().isOk());

        verify(aiMatchingService).streamChat(eq("user1@test.ac.kr"), any(AiMatchingChatRequestDto.class));
    }

    @Test
    @DisplayName("AI 매칭 대화 초기화 API는 인증 이메일과 대화 ID를 서비스로 전달하고 204를 반환한다")
    void clearConversation_shouldDelegateToService() throws Exception {
        mockMvc.perform(delete("/api/v1/ai/matching/chat/{conversationId}", "conversation-1")
                        .principal(userAuthentication(1L)))
                .andExpect(status().isNoContent());

        verify(aiMatchingService).clearConversation("user1@test.ac.kr", "conversation-1");
    }
}
