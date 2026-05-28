package com.example.team3final.domain.notification.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.ServiceException;
import com.example.team3final.domain.notification.dto.response.GetNotificationsResponseDto;
import com.example.team3final.domain.notification.dto.response.UpdateNotificationReadResponseDto;
import com.example.team3final.domain.notification.entity.Notification;
import com.example.team3final.domain.notification.enums.NotificationType;
import com.example.team3final.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService{

    private final NotificationRepository notificationRepository;

    // 알림 목록 조회
    @Override
    public PageResponseDto<GetNotificationsResponseDto> getNotifications(
            Long receiverId, Boolean isRead, NotificationType type, Pageable pageable) {

        Page<Notification> page;

        if (isRead != null && type != null) {
            // 읽음 여부 + 유형 필터
            page = notificationRepository.findByReceiverIdAndIsReadAndTypeOrderByCreatedAtDesc(
                    receiverId, isRead, type, pageable);
        } else if (isRead != null) {
            // 읽음 여부 필터만
            page = notificationRepository.findByReceiverIdAndIsReadOrderByCreatedAtDesc(
                    receiverId, isRead, pageable);
        } else if (type != null) {
            // 유형 필터만
            page = notificationRepository.findByReceiverIdAndTypeOrderByCreatedAtDesc(
                    receiverId, type, pageable);
        } else {
            // 전체 조회
            page = notificationRepository.findByReceiverIdOrderByCreatedAtDesc(
                    receiverId, pageable);
        }

        return PageResponseDto.from(page.map(GetNotificationsResponseDto::from));
    }

    // 단건 읽음 처리
    @Transactional
    @Override
    public UpdateNotificationReadResponseDto updateNotificationRead(Long receiverId, Long notificationId) {

        // 알림 존재 여부 확인
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ServiceException(ErrorCode.NOTIFICATION_NOT_FOUND));

        // 본인 알림 확인
        if (!notification.getReceiverId().equals(receiverId)) {
            throw new ServiceException(ErrorCode.NOTIFICATION_NOT_OWNER);
        }

        // 이미 읽은 알림이면 그냥 반환
        if (notification.isRead()) {
            return UpdateNotificationReadResponseDto.from(notification);
        }

        // 읽음 처리
        notification.markAsRead();

        return UpdateNotificationReadResponseDto.from(notification);
    }
}

