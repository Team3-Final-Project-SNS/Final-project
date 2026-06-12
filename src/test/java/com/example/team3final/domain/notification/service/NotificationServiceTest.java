package com.example.team3final.domain.notification.service;

import com.example.team3final.common.dto.response.CursorResponseDto;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.NotificationException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

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
    @DisplayName("알림 목록 조회 - 요청 크기가 50을 넘으면 50으로 제한한다")
    void getNotifications_LimitsPageSizeToFifty() {
        given(notificationRepository.findByReceiverTypeAndReceiverIdAndIdLessThan(
                any(), anyLong(), anyLong(), any(Pageable.class)))
                .willReturn(List.of());

        notificationService.getNotifications(NotificationReceiverType.USER, 1L, null, 100);

        verify(notificationRepository).findByReceiverTypeAndReceiverIdAndIdLessThan(
                eq(NotificationReceiverType.USER),
                eq(1L),
                eq(Long.MAX_VALUE),
                argThat(pageable -> pageable.getPageSize() == 51)
        );
    }

    @Test
    @DisplayName("알림 목록 조회 - 다음 페이지가 있으면 요청 크기만 반환하고 커서를 제공한다")
    void getNotifications_HasNextPage() {
        Notification first = createNotification(3L, 1L);
        Notification second = createNotification(2L, 1L);
        Notification extra = createNotification(1L, 1L);
        given(notificationRepository.findByReceiverTypeAndReceiverIdAndIdLessThan(
                any(), anyLong(), anyLong(), any(Pageable.class)))
                .willReturn(List.of(first, second, extra));

        CursorResponseDto<?> result =
                notificationService.getNotifications(NotificationReceiverType.USER, 1L, null, 2);

        assertThat(result.content()).hasSize(2);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isEqualTo(2L);
    }

    @Test
    @DisplayName("알림 목록 조회 - 0 이하 커서는 거부한다")
    void getNotifications_InvalidCursor() {
        assertNotificationError(
                () -> notificationService.getNotifications(NotificationReceiverType.USER, 1L, 0L, 20),
                ErrorCode.NOTIFICATION_INVALID_CURSOR
        );
        verifyNoInteractions(notificationRepository);
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
        assertThat(result.readAt()).isNotNull();
    }

    @Test
    @DisplayName("단건 알림 읽음 처리 - 알림이 없으면 실패")
    void updateNotificationRead_NotFound() {
        given(notificationRepository.findById(10L)).willReturn(Optional.empty());

        assertNotificationError(
                () -> notificationService.updateNotificationRead(NotificationReceiverType.USER, 1L, 10L),
                ErrorCode.NOTIFICATION_NOT_FOUND
        );
    }

    @Test
    @DisplayName("단건 알림 읽음 처리 - 다른 수신자의 알림이면 실패")
    void updateNotificationRead_Forbidden() {
        given(notificationRepository.findById(10L))
                .willReturn(Optional.of(createNotification(10L, 2L)));

        assertNotificationError(
                () -> notificationService.updateNotificationRead(NotificationReceiverType.USER, 1L, 10L),
                ErrorCode.NOTIFICATION_FORBIDDEN
        );
    }

    @Test
    @DisplayName("단건 알림 읽음 처리 - 이미 읽은 알림은 읽은 시각을 유지한다")
    void updateNotificationRead_AlreadyReadIsIdempotent() {
        Notification notification = createNotification(10L, 1L);
        notification.markAsRead();
        var originalReadAt = notification.getReadAt();
        given(notificationRepository.findById(10L)).willReturn(Optional.of(notification));

        UpdateNotificationReadResponseDto result =
                notificationService.updateNotificationRead(NotificationReceiverType.USER, 1L, 10L);

        assertThat(result.readAt()).isEqualTo(originalReadAt);
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

    private void assertNotificationError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(NotificationException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }
}
