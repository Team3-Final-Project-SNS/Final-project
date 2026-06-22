package com.example.team3final.domain.chat.controller;

import com.example.team3final.common.dto.response.CursorResponseDto;
import com.example.team3final.domain.chat.service.ChatQueryService;
import com.example.team3final.test.controller.ControllerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("채팅 컨트롤러 통합 테스트")
class ChatControllerTest extends ControllerTestSupport {

    @Mock
    private ChatQueryService chatQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvcFor(new ChatController(chatQueryService));
    }

    @Test
    @DisplayName("채팅 메시지 목록 조회 API는 커서와 크기를 서비스로 전달한다")
    void getChatMessages_shouldBindCursorAndSize() throws Exception {
        when(chatQueryService.getChatMessages(10L, 1L, 99L, 30))
                .thenReturn(new CursorResponseDto<>(List.of(), false, null));

        mockMvc.perform(get("/api/v1/chat-rooms/10/messages")
                        .with(authentication(userAuthentication(1L)))
                        .param("cursorId", "99")
                        .param("size", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(chatQueryService).getChatMessages(10L, 1L, 99L, 30);
    }

    @Test
    @DisplayName("채팅 참여자 목록 조회 API는 채팅방 ID와 사용자 ID를 서비스로 전달한다")
    void getChatMembers_shouldPassChatRoomIdAndUserId() throws Exception {
        when(chatQueryService.getChatMembers(10L, 1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/chat-rooms/10/members")
                        .with(authentication(userAuthentication(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(chatQueryService).getChatMembers(10L, 1L);
    }
}
