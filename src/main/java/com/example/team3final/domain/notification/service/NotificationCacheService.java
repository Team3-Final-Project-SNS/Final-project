package com.example.team3final.domain.notification.service;

import com.example.team3final.domain.notification.cache.NotificationCachePolicy;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

/**
 * 캐시 무효화 전용 서비스
 *
 * [왜 별도 클래스로 분리했나?]
 * @KafkaListener 메서드는 Spring AOP 프록시를 거치지 않기 때문에
 * @CacheEvict를 직접 붙여도 동작하지 않음.
 * → 캐시 무효화 로직을 별도 Spring Bean으로 분리하면
 *   이 클래스의 메서드는 프록시를 통해 호출되어 @CacheEvict 정상 동작.
 */
@Service
public class NotificationCacheService {

    /**
     * 알림 관련 캐시 전체 무효화
     * - NOTIFICATION_LIST: 알림 목록 캐시
     * - NOTIFICATION_UNREAD: 미확인 카운트 캐시
     *
     * allEntries = true: 해당 캐시 이름의 모든 키 삭제
     * (특정 유저 키만 삭제하려면 receiverId가 필요한데,
     *  Kafka 이벤트에서 receiverId는 있지만 캐시 키 형식을
     *  완전히 맞추기 어려워 전체 삭제로 처리)
     */
    @Caching(evict = {
            @CacheEvict(
                    cacheNames = NotificationCachePolicy.NOTIFICATION_LIST,
                    allEntries = true   // 모든 유저의 알림 목록 캐시 삭제
            ),
            @CacheEvict(
                    cacheNames = NotificationCachePolicy.NOTIFICATION_UNREAD,
                    allEntries = true   // 모든 유저의 미확인 카운트 캐시 삭제
            )
    })
    public void evictAll() {
        // 캐시 무효화만 담당 — 비즈니스 로직 없음
        // Spring이 이 메서드 호출 전에 @CacheEvict를 먼저 처리함
    }
}