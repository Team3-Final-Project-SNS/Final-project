package com.example.team3final.domain.admin.notification.controller;

import com.example.team3final.common.dto.response.CursorResponseDto;
import com.example.team3final.domain.notification.enums.NotificationReceiverType;
import com.example.team3final.domain.notification.service.NotificationService;
import com.example.team3final.test.controller.ControllerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("관리자 알림 컨트롤러 통합 테스트")
class AdminNotificationControllerTest extends ControllerTestSupport {

    @Mock
    private NotificationService notificationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvcFor(new AdminNotificationController(notificationService));
    }

    @Test
    @DisplayName("관리자 알림 목록 조회 API는 관리자 수신자 타입과 커서를 서비스로 전달한다")
    void getNotifications_shouldDelegateCursorRequest() throws Exception {
        when(notificationService.getNotifications(NotificationReceiverType.ADMIN, 1L, 99L, 30))
                .thenReturn(new CursorResponseDto<>(List.of(), false, null));

        mockMvc.perform(get("/api/v1/admin/notifications")
                        .with(authentication(adminAuthentication(1L)))
                        .param("cursorId", "99")
                        .param("size", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(notificationService).getNotifications(NotificationReceiverType.ADMIN, 1L, 99L, 30);
    }

    @Test
    @DisplayName("관리자 전체 알림 읽음 API는 관리자 수신자 타입과 관리자 ID를 서비스로 전달한다")
    void readAll_shouldDelegateAdminReceiver() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/notifications/read-all")
                        .with(authentication(adminAuthentication(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(notificationService).updateAllNotificationsRead(NotificationReceiverType.ADMIN, 1L);
    }

    @Test
    @DisplayName("관리자 단일 알림 읽음 API는 알림 ID와 관리자 ID를 서비스로 전달한다")
    void read_shouldDelegateNotificationId() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/notifications/10/read")
                        .with(authentication(adminAuthentication(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(notificationService).updateNotificationRead(NotificationReceiverType.ADMIN, 1L, 10L);
    }

    @Test
    @DisplayName("관리자 읽지 않은 알림 수 조회 API는 관리자 수신자 타입과 관리자 ID를 서비스로 전달한다")
    void getUnreadCount_shouldDelegateAdminReceiver() throws Exception {
        mockMvc.perform(get("/api/v1/admin/notifications/unread-count")
                        .with(authentication(adminAuthentication(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(notificationService).getUnreadCount(NotificationReceiverType.ADMIN, 1L);
    }

    @Test
    @DisplayName("관리자 알림 SSE 구독 API는 관리자 수신자 타입과 관리자 ID를 서비스로 전달한다")
    void subscribe_shouldDelegateAdminReceiver() throws Exception {
        when(notificationService.subscribe(NotificationReceiverType.ADMIN, 1L)).thenReturn(new SseEmitter());

        mockMvc.perform(get("/api/v1/admin/notifications/subscribe")
                        .with(authentication(adminAuthentication(1L))))
                .andExpect(status().isOk());

        verify(notificationService).subscribe(NotificationReceiverType.ADMIN, 1L);
    }
}
