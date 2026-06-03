package com.example.team3final.domain.notification.service;

import com.example.team3final.common.dto.response.CursorResponseDto;
import com.example.team3final.domain.notification.dto.response.GetNotificationsResponseDto;
import com.example.team3final.domain.notification.dto.response.GetUnreadCountResponseDto;
import com.example.team3final.domain.notification.dto.response.UpdateAllNotificationsReadResponseDto;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface NotificationService {

    // 알림 목록 조회 (커서 기반 페이징)
    // cursorId: 마지막으로 받은 알림 ID (처음 요청 시 Long.MAX_VALUE)
    CursorResponseDto<GetNotificationsResponseDto> getNotifications(
            Long receiverId, Long cursorId, int size);

   // 전체 읽음 처리
    UpdateAllNotificationsReadResponseDto updateAllNotificationsRead(Long receiverId);

    // 미확인 알림 카운트
    GetUnreadCountResponseDto getUnreadCount(Long receiverId);

    // SSE 연결
    SseEmitter subscribe(Long userId);
}

