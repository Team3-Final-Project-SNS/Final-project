package com.example.team3final.domain.ai.support.controller;

import com.example.team3final.domain.ai.support.dto.request.AiSupportChatRequestDto;
import com.example.team3final.domain.ai.support.service.AiSupportService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("AI 고객지원 컨트롤러 통합 테스트")
class AiSupportControllerTest extends ControllerTestSupport {

    @Mock
    private AiSupportService aiSupportService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvcFor(new AiSupportController(aiSupportService));
    }

    @Test
    @DisplayName("AI 고객지원 스트림 API는 사용자 ID와 이메일, 요청 본문을 서비스로 전달한다")
    void streamChat_shouldDelegateToService() throws Exception {
        when(aiSupportService.streamChat(eq(1L), eq("user1@test.ac.kr"), any(AiSupportChatRequestDto.class)))
                .thenReturn(Flux.just("상담 응답"));

        mockMvc.perform(post("/api/v1/ai/support/chat/stream")
                        .with(authentication(userAuthentication(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": "conversation-1",
                                  "message": "결제 문의가 있어요"
                                }
                                """))
                .andExpect(status().isOk());

        verify(aiSupportService).streamChat(eq(1L), eq("user1@test.ac.kr"), any(AiSupportChatRequestDto.class));
    }
}
