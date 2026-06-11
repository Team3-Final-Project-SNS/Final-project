package com.example.team3final.domain.notification.service;

import com.example.team3final.common.dto.response.CursorResponseDto;
import com.example.team3final.domain.notification.dto.response.GetUnreadCountResponseDto;
import com.example.team3final.domain.notification.dto.response.UpdateAllNotificationsReadResponseDto;
import com.example.team3final.domain.notification.dto.response.UpdateNotificationReadResponseDto;
import com.example.team3final.domain.notification.entity.Notification;
import com.example.team3final.domain.notification.enums.NotificationReceiverType;
import com.example.team3final.domain.notification.enums.NotificationType;
import com.example.team3final.domain.notification.enums.RelatedDomain;
import com.example.team3final.domain.notification.repository.NotificationRepository;
import com.example.team3final.domain.notification.sse.SseEmitterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private SseEmitterRepository sseEmitterRepository;

    @Test
    @DisplayName("알림 목록 조회 - 성공")
    void getNotifications_Success() {
        // given
        NotificationReceiverType type = NotificationReceiverType.USER;
        Long receiverId = 1L;

        given(notificationRepository.findByReceiverTypeAndReceiverIdAndIdLessThan(any(), anyLong(), anyLong(), any(Pageable.class)))
                .willReturn(List.of());

        // when
        CursorResponseDto result = notificationService.getNotifications(type, receiverId, null, 20);

        // then
        assertThat(result.content()).isEmpty();
    }

    @Test
    @DisplayName("전체 알림 읽음 처리 - 성공")
    void updateAllNotificationsRead_Success() {
        given(notificationRepository.markAllAsRead(eq(NotificationReceiverType.USER), eq(1L), any()))
                .willReturn(3);

        UpdateAllNotificationsReadResponseDto result =
                notificationService.updateAllNotificationsRead(NotificationReceiverType.USER, 1L);

        assertThat(result.updatedCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("단건 알림 읽음 처리 - 성공")
    void updateNotificationRead_Success() {
        Notification notification = createNotification(10L, 1L);
        given(notificationRepository.findById(10L)).willReturn(Optional.of(notification));

        UpdateNotificationReadResponseDto result =
                notificationService.updateNotificationRead(NotificationReceiverType.USER, 1L, 10L);

        assertThat(result.notificationId()).isEqualTo(10L);
        assertThat(result.isRead()).isTrue();
    }

    @Test
    @DisplayName("읽지 않은 알림 수 조회 - 성공")
    void getUnreadCount_Success() {
        given(notificationRepository.countByReceiverTypeAndReceiverIdAndIsRead(NotificationReceiverType.USER, 1L, false))
                .willReturn(5L);

        GetUnreadCountResponseDto result =
                notificationService.getUnreadCount(NotificationReceiverType.USER, 1L);

        assertThat(result.unreadCount()).isEqualTo(5L);
    }

    @Test
    @DisplayName("SSE 구독 - 성공")
    void subscribe_Success() {
        SseEmitter result = notificationService.subscribe(NotificationReceiverType.USER, 1L);

        assertThat(result).isNotNull();
        verify(sseEmitterRepository).save(eq(NotificationReceiverType.USER), eq(1L), any(SseEmitter.class));
    }

    private Notification createNotification(Long id, Long receiverId) {
        Notification notification = Notification.builder()
                .receiverId(receiverId)
                .receiverType(NotificationReceiverType.USER)
                .type(NotificationType.POST_EXPIRED)
                .title("title")
                .content("content")
                .relatedDomain(RelatedDomain.POST)
                .relatedId(100L)
                .build();
        ReflectionTestUtils.setField(notification, "id", id);
        return notification;
    }
}
