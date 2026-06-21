package com.example.team3final.domain.notification.service;

import com.example.team3final.common.dto.response.CursorResponseDto;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.NotificationException;
import com.example.team3final.domain.notification.cache.NotificationCachePolicy;
import com.example.team3final.domain.notification.dto.response.GetNotificationsResponseDto;
import com.example.team3final.domain.notification.dto.response.GetUnreadCountResponseDto;
import com.example.team3final.domain.notification.dto.response.UpdateAllNotificationsReadResponseDto;
import com.example.team3final.domain.notification.dto.response.UpdateNotificationReadResponseDto;
import com.example.team3final.domain.notification.entity.Notification;
import com.example.team3final.domain.notification.enums.NotificationReceiverType;
import com.example.team3final.domain.notification.repository.NotificationRepository;
import com.example.team3final.domain.notification.sse.SseEmitterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
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
            NotificationReceiverType receiverType, Long receiverId, Long cursorId, int size) {

        // size 최대 제한
        int safeSize = Math.min(size, 50);

        // cursorId 없으면 처음부터 조회 (Long.MAX_VALUE로 처리)
        Long effectiveCursorId = (cursorId != null) ? cursorId : Long.MAX_VALUE;

        // cursorId 유효성 검증
        if (effectiveCursorId <= 0) {
            throw new NotificationException(ErrorCode.NOTIFICATION_INVALID_CURSOR);
        }

        // cursorId 미만 알림 조회 (size+1개로 다음 페이지 여부 확인)
        List<Notification> notifications = notificationRepository
                .findByReceiverTypeAndReceiverIdAndIdLessThan(
                        receiverType, receiverId, effectiveCursorId, PageRequest.of(0, safeSize + 1));

        // DTO 변환
        List<GetNotificationsResponseDto> content = notifications.stream()
                .map(GetNotificationsResponseDto::from)
                .toList();

        return CursorResponseDto.of(content, safeSize, GetNotificationsResponseDto::notificationId);
    }

    // 전체 읽음 처리
    @Override
    @Transactional
    @Caching(evict = {
            // 미확인 카운트 캐시 삭제 - 전체 읽음 후 숫자 즉시 반영
            @CacheEvict(
                    cacheNames = NotificationCachePolicy.NOTIFICATION_UNREAD,
                    key = "#receiverType + ':' + #receiverId"
            )
    })
    public UpdateAllNotificationsReadResponseDto updateAllNotificationsRead(
            NotificationReceiverType receiverType, Long receiverId) {

        // 벌크 업데이트 - 미읽은 알림 전체 읽음 처리
        // 별도 검증 불필요
        // - receiverId 조건으로 본인 알림만 업데이트 (타인 알림 접근 불가)
        int updatedCount = notificationRepository.markAllAsRead(
                receiverType, receiverId, LocalDateTime.now());

        return UpdateAllNotificationsReadResponseDto.from(updatedCount);
    }

    // 단건 읽음 처리
    @Override
    @Transactional
    @Caching(evict = {
            // 미확인 카운트 캐시 삭제 - 읽음 처리 후 숫자 즉시 반영
            @CacheEvict(
                    cacheNames = NotificationCachePolicy.NOTIFICATION_UNREAD,
                    key = "#receiverType + ':' + #receiverId"
            )
    })
    public UpdateNotificationReadResponseDto updateNotificationRead(
            NotificationReceiverType receiverType, Long receiverId, Long notificationId) {

        // 알림 존재 여부 확인 → 없으면 404
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationException(ErrorCode.NOTIFICATION_NOT_FOUND));

        // 본인 알림인지 확인 → 타인 알림이면 403
        if (notification.getReceiverType() != receiverType
                || !notification.getReceiverId().equals(receiverId)) {
            throw new NotificationException(ErrorCode.NOTIFICATION_FORBIDDEN);
        }

        // 이미 읽은 알림이면 멱등하게 현재 상태 그대로 반환 (중복 처리 방지)
        if (notification.isRead()) {
            return new UpdateNotificationReadResponseDto(
                    notification.getId(),
                    true,
                    notification.getReadAt()
            );
        }

        // 읽음 처리
        notification.markAsRead();

        return new UpdateNotificationReadResponseDto(
                notification.getId(),
                true,
                notification.getReadAt()
        );
    }

    // 미확인 알림 카운트
    @Override
    // 미확인 알림 카운트를 Redis에 캐싱
    // TTL 10초 — 벨 아이콘 숫자는 실시간성 중요하므로 짧게 설정 (CacheConfig 참고)
    @Cacheable(
            cacheNames = NotificationCachePolicy.NOTIFICATION_UNREAD,
            key = "#receiverType + ':' + #receiverId"
    )
    public GetUnreadCountResponseDto getUnreadCount(
            NotificationReceiverType receiverType, Long receiverId) {

        // 별도 검증 불필요
        // - receiverId 조건으로 본인 알림만 카운트 (타인 알림 접근 불가)
        long unreadCount = notificationRepository
                .countByReceiverTypeAndReceiverIdAndIsRead(receiverType, receiverId, false);

        return GetUnreadCountResponseDto.from(unreadCount);
    }

    // SSE 연결
    @Override
    public SseEmitter subscribe(NotificationReceiverType receiverType, Long receiverId) {

        // SSE 타임아웃 30분 설정
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        // Emitter 저장
        sseEmitterRepository.save(receiverType, receiverId, emitter);

        // 연결 종료 시 Emitter 삭제
        emitter.onCompletion(() -> sseEmitterRepository.delete(receiverType, receiverId));
        emitter.onTimeout(() -> sseEmitterRepository.delete(receiverType, receiverId));
        emitter.onError(e -> sseEmitterRepository.delete(receiverType, receiverId));

        // 연결 직후 더미 이벤트 전송 (SSE 연결 확인용)
        // 브라우저는 첫 이벤트를 받아야 연결이 완료됨
        try {
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data("SSE 연결 완료"));
        } catch (IOException e) {
            sseEmitterRepository.delete(receiverType, receiverId);
        }

        return emitter;
    }
}
