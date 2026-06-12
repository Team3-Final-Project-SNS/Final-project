package com.example.team3final.domain.notification.controller;

import com.example.team3final.common.dto.response.CursorResponseDto;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.NotificationException;
import com.example.team3final.domain.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.example.team3final.test.security.WithMockCustomUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("API test")
    @WithMockCustomUser
    void getNotifications_ApiTest() throws Exception {
        // given
        CursorResponseDto response = CursorResponseDto.of(List.of(), 20, (o) -> 1L);
        given(notificationService.getNotifications(any(), anyLong(), any(), anyInt())).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        verify(notificationService).getNotifications(any(), anyLong(), any(), anyInt());
    }

    @Test
    @DisplayName("알림 목록 조회 API - 잘못된 커서를 400으로 반환한다")
    @WithMockCustomUser
    void getNotifications_InvalidCursor() throws Exception {
        given(notificationService.getNotifications(any(), anyLong(), any(), anyInt()))
                .willThrow(new NotificationException(ErrorCode.NOTIFICATION_INVALID_CURSOR));

        mockMvc.perform(get("/api/v1/notifications").param("cursorId", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOTI_002"));
    }

    @Test
    @DisplayName("API test")
    @WithMockCustomUser
    void updateAllNotificationsRead_ApiTest() throws Exception {
        // given
        given(notificationService.updateAllNotificationsRead(any(), anyLong())).willReturn(null);

        // when & then
        mockMvc.perform(patch("/api/v1/notifications/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("API test")
    @WithMockCustomUser
    void updateNotificationRead_ApiTest() throws Exception {
        // given
        given(notificationService.updateNotificationRead(any(), anyLong(), anyLong())).willReturn(null);

        // when & then
        mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("API test")
    @WithMockCustomUser
    void subscribe_ApiTest() throws Exception {
        // given
        given(notificationService.subscribe(any(), anyLong())).willReturn(new SseEmitter());

        // when & then
        mockMvc.perform(get("/api/v1/notifications/subscribe"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("API test")
    @WithMockCustomUser
    void getUnreadCount_ApiTest() throws Exception {
        // given
        given(notificationService.getUnreadCount(any(), anyLong())).willReturn(null);

        // when & then
        mockMvc.perform(get("/api/v1/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }
}
