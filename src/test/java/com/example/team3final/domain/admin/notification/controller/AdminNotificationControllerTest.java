package com.example.team3final.domain.admin.notification.controller;

import com.example.team3final.common.dto.response.CursorResponseDto;
import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.enums.AdminRole;
import com.example.team3final.domain.admin.security.AdminDetailsImpl;
import com.example.team3final.domain.notification.dto.response.GetNotificationsResponseDto;
import com.example.team3final.domain.notification.dto.response.GetUnreadCountResponseDto;
import com.example.team3final.domain.notification.dto.response.UpdateAllNotificationsReadResponseDto;
import com.example.team3final.domain.notification.dto.response.UpdateNotificationReadResponseDto;
import com.example.team3final.domain.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminNotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @Autowired
    private ObjectMapper objectMapper;

    private AdminDetailsImpl createMockAdminDetails() {
        Admin admin = Admin.builder()
                .email("admin@test.com")
                .role(AdminRole.SUPER_ADMIN)
                .build();
        ReflectionTestUtils.setField(admin, "id", 1L);
        return new AdminDetailsImpl(admin);
    }

    @Test
    @DisplayName("관리자 알람 목록 조회 API test")
    void getNotifications_ApiTest() throws Exception {
        // given
        CursorResponseDto<GetNotificationsResponseDto> response =
                new CursorResponseDto<>(List.of(), false, null);
        given(notificationService.getNotifications(any(), anyLong(), any(), anyInt())).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/admin/notifications")
                        .with(user(createMockAdminDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("API test")
    void readAll_ApiTest() throws Exception {
        // given
        UpdateAllNotificationsReadResponseDto response = new UpdateAllNotificationsReadResponseDto(5);
        given(notificationService.updateAllNotificationsRead(any(), anyLong())).willReturn(response);

        // when & then
        mockMvc.perform(patch("/api/v1/admin/notifications/read-all")
                        .with(user(createMockAdminDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("API test")
    void read_ApiTest() throws Exception {
        // given
        UpdateNotificationReadResponseDto response = new UpdateNotificationReadResponseDto(1L, true, LocalDateTime.now());
        given(notificationService.updateNotificationRead(any(), anyLong(), anyLong())).willReturn(response);

        // when & then
        mockMvc.perform(patch("/api/v1/admin/notifications/1/read")
                        .with(user(createMockAdminDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("API test")
    void getUnreadCount_ApiTest() throws Exception {
        // given
        GetUnreadCountResponseDto response = new GetUnreadCountResponseDto(3L);
        given(notificationService.getUnreadCount(any(), anyLong())).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/admin/notifications/unread-count")
                        .with(user(createMockAdminDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("API test")
    void subscribe_ApiTest() throws Exception {
        // given
        given(notificationService.subscribe(any(), anyLong())).willReturn(new SseEmitter());

        // when & then
        mockMvc.perform(get("/api/v1/admin/notifications/subscribe")
                        .with(user(createMockAdminDetails())))
                .andExpect(status().isOk());
    }
}
