package com.example.team3final.domain.notification.service;

import com.example.team3final.common.dto.response.CursorResponseDto;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.NotificationException;
import com.example.team3final.domain.notification.dto.response.GetNotificationsResponseDto;
import com.example.team3final.domain.notification.dto.response.GetUnreadCountResponseDto;
import com.example.team3final.domain.notification.dto.response.UpdateAllNotificationsReadResponseDto;
import com.example.team3final.domain.notification.entity.Notification;
import com.example.team3final.domain.notification.repository.NotificationRepository;
import com.example.team3final.domain.notification.sse.SseEmitterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final SseEmitterRepository sseEmitterRepository;

    // 알림 목록 조회 (커서 기반 페이징)
    @Override
    public CursorResponseDto<GetNotificationsResponseDto> getNotifications(
            Long receiverId, Long cursorId, int size) {

        // size 최대 제한
        int safeSize = Math.min(size, 50);

        // cursorId 유효성 검증
        if (cursorId <= 0) {
            throw new NotificationException(ErrorCode.NOTIFICATION_INVALID_CURSOR);
        }

        // cursorId 미만 알림 조회 (size+1개로 다음 페이지 여부 확인)
        List<Notification> notifications = notificationRepository
                .findByReceiverIdAndIdLessThan(
                        receiverId, cursorId, PageRequest.of(0, safeSize + 1));

        // DTO 변환
        List<GetNotificationsResponseDto> content = notifications.stream()
                .map(GetNotificationsResponseDto::from)
                .toList();

        return CursorResponseDto.of(content, safeSize, GetNotificationsResponseDto::notificationId);
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

