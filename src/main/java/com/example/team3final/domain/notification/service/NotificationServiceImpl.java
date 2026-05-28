package com.example.team3final.domain.notification.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.notification.dto.response.GetNotificationsResponseDto;
import com.example.team3final.domain.notification.dto.response.UpdateAllNotificationsReadResponseDto;
import com.example.team3final.domain.notification.entity.Notification;
import com.example.team3final.domain.notification.enums.NotificationType;
import com.example.team3final.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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

    // 전체 읽음 처리
    @Override
    @Transactional
    public UpdateAllNotificationsReadResponseDto updateAllNotificationsRead(Long receiverId) {

        // 벌크 업데이트 - 미읽은 알림 전체 읽음 처리
        // 별도 검증 불필요
        // - receiverId 조건으로 본인 알림만 업데이트 (타인 알림 접근 불가)
        int updatedCount = notificationRepository.markAllAsRead(receiverId, LocalDateTime.now());

        return UpdateAllNotificationsReadResponseDto.from(updatedCount);
    }
}

