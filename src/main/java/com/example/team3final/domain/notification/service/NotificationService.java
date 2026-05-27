package com.example.team3final.domain.notification.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.notification.dto.response.GetNotificationsResponseDto;
import com.example.team3final.domain.notification.enums.NotificationType;
import org.springframework.data.domain.Pageable;

public interface NotificationService {

    // 알림 목록 조회
    PageResponseDto<GetNotificationsResponseDto> getNotifications(Long receiverId, Boolean isRead,
                                                                  NotificationType type, Pageable pageable);

}

