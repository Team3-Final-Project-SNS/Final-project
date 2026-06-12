package com.example.team3final.domain.chat.controller;

import com.example.team3final.common.dto.response.CursorResponseDto;
import com.example.team3final.common.exception.ChatException;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.domain.chat.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.example.team3final.test.security.WithMockCustomUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ChatController.class)
@AutoConfigureMockMvc(addFilters = false)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("API test")
    @WithMockCustomUser
    void getChatMessages_ApiTest() throws Exception {
        // given
        Long chatRoomId = 1L;
        CursorResponseDto response = CursorResponseDto.of(List.of(), 20, (o) -> 1L);

        given(chatService.getChatMessages(anyLong(), anyLong(), anyLong(), anyInt())).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/chat-rooms/{chatRoomId}/messages", chatRoomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        verify(chatService).getChatMessages(chatRoomId, 1L, 9_999_999_999L, 20);
    }

    @Test
    @DisplayName("채팅 메시지 조회 API - 잘못된 페이지 크기를 400으로 반환한다")
    @WithMockCustomUser
    void getChatMessages_InvalidPageSize() throws Exception {
        given(chatService.getChatMessages(anyLong(), anyLong(), anyLong(), anyInt()))
                .willThrow(new ChatException(ErrorCode.CHAT_INVALID_PAGE_SIZE));

        mockMvc.perform(get("/api/v1/chat-rooms/{chatRoomId}/messages", 1L)
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CHAT_005"));
    }

    @Test
    @DisplayName("API test")
    @WithMockCustomUser
    void getChatMembers_ApiTest() throws Exception {
        // given
        Long chatRoomId = 1L;
        given(chatService.getChatMembers(anyLong(), anyLong())).willReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/v1/chat-rooms/{chatRoomId}/members", chatRoomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }
}
