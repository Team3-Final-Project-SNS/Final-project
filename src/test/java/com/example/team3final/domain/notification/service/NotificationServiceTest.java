package com.example.team3final.domain.notification.service;

import com.example.team3final.common.dto.response.CursorResponseDto;
import com.example.team3final.common.exception.NotificationException;
import com.example.team3final.domain.notification.dto.response.GetNotificationsResponseDto;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("알림 서비스 단위 테스트")
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private SseEmitterRepository sseEmitterRepository;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Test
    @DisplayName("알림 목록 조회는 커서와 크기를 보정해 알림 목록을 반환한다")
    void getNotifications_shouldReturnCursorResponse() {
        Notification notification = notification(1L, 10L, false);
        when(notificationRepository.findByReceiverTypeAndReceiverIdAndIdLessThan(
                eq(NotificationReceiverType.USER), eq(10L), eq(Long.MAX_VALUE), any(Pageable.class)))
                .thenReturn(List.of(notification));

        CursorResponseDto<GetNotificationsResponseDto> result =
                notificationService.getNotifications(NotificationReceiverType.USER, 10L, null, 100);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).notificationId()).isEqualTo(1L);
        verify(notificationRepository).findByReceiverTypeAndReceiverIdAndIdLessThan(
                eq(NotificationReceiverType.USER), eq(10L), eq(Long.MAX_VALUE), any(Pageable.class));
    }

    @Test
    @DisplayName("알림 목록 조회는 0 이하 커서이면 알림 예외를 던진다")
    void getNotifications_shouldThrowWhenCursorIsInvalid() {
        assertThatThrownBy(() -> notificationService.getNotifications(NotificationReceiverType.USER, 10L, 0L, 10))
                .isInstanceOf(NotificationException.class);
    }

    @Test
    @DisplayName("전체 알림 읽음 처리는 읽음 처리 개수를 반환한다")
    void updateAllNotificationsRead_shouldReturnUpdatedCount() {
        when(notificationRepository.markAllAsRead(eq(NotificationReceiverType.USER), eq(10L), any(LocalDateTime.class)))
                .thenReturn(3);

        UpdateAllNotificationsReadResponseDto result =
                notificationService.updateAllNotificationsRead(NotificationReceiverType.USER, 10L);

        assertThat(result.updatedCount()).isEqualTo(3);
        verify(notificationRepository).markAllAsRead(eq(NotificationReceiverType.USER), eq(10L), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("단건 알림 읽음 처리는 본인 알림을 읽음 상태로 변경한다")
    void updateNotificationRead_shouldMarkNotificationAsRead() {
        Notification notification = notification(1L, 10L, false);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        UpdateNotificationReadResponseDto result =
                notificationService.updateNotificationRead(NotificationReceiverType.USER, 10L, 1L);

        assertThat(result.notificationId()).isEqualTo(1L);
        assertThat(result.isRead()).isTrue();
        assertThat(notification.isRead()).isTrue();
        verify(notificationRepository).findById(1L);
    }

    @Test
    @DisplayName("단건 알림 읽음 처리는 존재하지 않는 알림이면 예외를 던진다")
    void updateNotificationRead_shouldThrowWhenNotificationNotFound() {
        when(notificationRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.updateNotificationRead(NotificationReceiverType.USER, 10L, 1L))
                .isInstanceOf(NotificationException.class);
    }

    @Test
    @DisplayName("단건 알림 읽음 처리는 다른 수신자의 알림이면 예외를 던진다")
    void updateNotificationRead_shouldThrowWhenNotificationBelongsToOtherReceiver() {
        Notification notification = notification(1L, 99L, false);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.updateNotificationRead(NotificationReceiverType.USER, 10L, 1L))
                .isInstanceOf(NotificationException.class);
    }

    @Test
    @DisplayName("미읽음 알림 수 조회는 저장소 count 결과를 반환한다")
    void getUnreadCount_shouldReturnUnreadCount() {
        when(notificationRepository.countByReceiverTypeAndReceiverIdAndIsRead(NotificationReceiverType.USER, 10L, false))
                .thenReturn(5L);

        GetUnreadCountResponseDto result = notificationService.getUnreadCount(NotificationReceiverType.USER, 10L);

        assertThat(result.unreadCount()).isEqualTo(5L);
        verify(notificationRepository).countByReceiverTypeAndReceiverIdAndIsRead(NotificationReceiverType.USER, 10L, false);
    }

    @Test
    @DisplayName("SSE 구독은 emitter를 저장하고 반환한다")
    void subscribe_shouldSaveEmitter() {
        SseEmitter emitter = notificationService.subscribe(NotificationReceiverType.USER, 10L);

        assertThat(emitter).isNotNull();
        verify(sseEmitterRepository).save(eq(NotificationReceiverType.USER), eq(10L), any(SseEmitter.class));
    }

    private Notification notification(Long notificationId, Long receiverId, boolean isRead) {
        Notification notification = Notification.builder()
                .receiverId(receiverId)
                .receiverType(NotificationReceiverType.USER)
                .type(NotificationType.SYSTEM)
                .title("알림")
                .content("알림 내용")
                .relatedDomain(RelatedDomain.SYSTEM)
                .relatedId(1L)
                .build();
        ReflectionTestUtils.setField(notification, "id", notificationId);
        if (isRead) {
            notification.markAsRead();
        }
        return notification;
    }
}
