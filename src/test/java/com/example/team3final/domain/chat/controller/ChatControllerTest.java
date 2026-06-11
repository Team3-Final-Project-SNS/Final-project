package com.example.team3final.domain.chat.controller;

import com.example.team3final.common.dto.response.CursorResponseDto;
import com.example.team3final.domain.chat.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.example.team3final.test.security.WithMockCustomUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
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
