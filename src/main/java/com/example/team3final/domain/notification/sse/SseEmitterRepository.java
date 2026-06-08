package com.example.team3final.domain.notification.sse;

import com.example.team3final.domain.notification.enums.NotificationReceiverType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * - 유저별 SseEmitter를 메모리에 저장/관리
 * - ConcurrentHashMap: 멀티스레드 환경에서 동시성 안전한 Map
 *   (일반 HashMap은 동시 접근 시 데이터 손상 가능)
 */

// SSE Emitter 저장소
@Slf4j
@Repository
public class SseEmitterRepository {

    // 유저 ID → SseEmitter 저장소 (thread-safe)
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    // Emitter 저장
    public void save(NotificationReceiverType receiverType, Long receiverId, SseEmitter emitter) {
        emitters.put(key(receiverType, receiverId), emitter);
        log.info("[SSE] Emitter 저장 - receiverType: {}, receiverId: {}", receiverType, receiverId);
    }

    // Emitter 조회
    public Optional<SseEmitter> find(NotificationReceiverType receiverType, Long receiverId) {
        return Optional.ofNullable(emitters.get(key(receiverType, receiverId)));
    }

    // Emitter 삭제 (연결 종료 시)
    public void delete(NotificationReceiverType receiverType, Long receiverId) {
        emitters.remove(key(receiverType, receiverId));
        log.info("[SSE] Emitter 삭제 - receiverType: {}, receiverId: {}", receiverType, receiverId);
    }

    private String key(NotificationReceiverType receiverType, Long receiverId) {
        return receiverType.name() + ":" + receiverId;
    }
}
