package com.example.team3final.domain.ai.report.controller;

import com.example.team3final.domain.ai.report.dto.request.AiReportChatRequestDto;
import com.example.team3final.domain.ai.report.service.AiReportService;
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
@DisplayName("AI 신고 분석 컨트롤러 통합 테스트")
class AiReportControllerTest extends ControllerTestSupport {

    @Mock
    private AiReportService aiReportService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvcFor(new AiReportController(aiReportService));
    }

    @Test
    @DisplayName("AI 신고 분석 스트림 API는 관리자 ID와 요청 본문을 서비스로 전달한다")
    void streamChat_shouldDelegateToService() throws Exception {
        when(aiReportService.streamChat(eq(1L), any(AiReportChatRequestDto.class)))
                .thenReturn(Flux.just("분석 응답"));

        mockMvc.perform(post("/api/v1/admin/ai/reports/chat/stream")
                        .with(authentication(adminAuthentication(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": "conversation-1",
                                  "message": "고위험 신고를 분석해줘"
                                }
                                """))
                .andExpect(status().isOk());

        verify(aiReportService).streamChat(eq(1L), any(AiReportChatRequestDto.class));
    }
    @Test
    @DisplayName("AI 관리자 콘솔 별칭 경로도 동일한 신고 분석 스트림 서비스로 위임한다")
    void streamChatWithConsoleAlias_shouldDelegateToService() throws Exception {
        when(aiReportService.streamChat(eq(1L), any(AiReportChatRequestDto.class)))
                .thenReturn(Flux.just("분석 응답"));

        mockMvc.perform(post("/api/v1/admin/ai/console/chat/stream")
                        .with(authentication(adminAuthentication(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": "conversation-1",
                                  "message": "관리자 콘솔에서 신고를 분석해줘"
                                }
                                """))
                .andExpect(status().isOk());

        verify(aiReportService).streamChat(eq(1L), any(AiReportChatRequestDto.class));
    }
}
