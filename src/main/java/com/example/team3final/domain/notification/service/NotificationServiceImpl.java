package com.example.team3final.domain.notification.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.notification.dto.response.GetNotificationsResponseDto;
import com.example.team3final.domain.notification.dto.response.GetUnreadCountResponseDto;
import com.example.team3final.domain.notification.dto.response.UpdateAllNotificationsReadResponseDto;
import com.example.team3final.domain.notification.entity.Notification;
import com.example.team3final.domain.notification.enums.NotificationType;
import com.example.team3final.domain.notification.repository.NotificationRepository;
import com.example.team3final.domain.notification.sse.SseEmitterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final SseEmitterRepository sseEmitterRepository;

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

    // 미확인 알림 카운트
    @Override
    public GetUnreadCountResponseDto getUnreadCount(Long receiverId) {

        // 별도 검증 불필요
        // - receiverId 조건으로 본인 알림만 카운트 (타인 알림 접근 불가)
        long unreadCount = notificationRepository.countByReceiverIdAndIsRead(receiverId, false);

        return GetUnreadCountResponseDto.from(unreadCount);
    }

    // SSE 연결
    @Override
    public SseEmitter subscribe(Long userId) {

        // SSE 타임아웃 30분 설정
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        // Emitter 저장
        sseEmitterRepository.save(userId, emitter);

        // 연결 종료 시 Emitter 삭제
        emitter.onCompletion(() -> sseEmitterRepository.deleteByUserId(userId));  // 정상 종료
        emitter.onTimeout(() -> sseEmitterRepository.deleteByUserId(userId));     // 타임아웃
        emitter.onError(e -> sseEmitterRepository.deleteByUserId(userId));        // 에러

        // 연결 직후 더미 이벤트 전송 (SSE 연결 확인용)
        // 브라우저는 첫 이벤트를 받아야 연결이 완료됨
        try {
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data("SSE 연결 완료"));
        } catch (IOException e) {
            sseEmitterRepository.deleteByUserId(userId);
        }

        return emitter;
    }
}

