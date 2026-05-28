package com.example.team3final.domain.notification.scheduler;

import com.example.team3final.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final NotificationRepository notificationRepository;

    /**
     * 10일 경과 알림 자동 삭제 스케줄러
     * 매일 새벽 3시에 실행
     * - 생성일 기준 10일 경과한 알림 하드 딜리트
     * - 소프트 딜리트 불필요! (알림은 이력 보존 필요 없음)
     */

    @Scheduled(cron = "0 0 3 * * *")  // 매일 새벽 3시 실행
    @Transactional
    public void deleteOldNotifications() {

        // 10일 전 시각 계산
        LocalDateTime cutoff = LocalDateTime.now().minusDays(10);

        // 10일 경과 알림 삭제
        notificationRepository.deleteByCreatedAtBefore(cutoff);

        log.info("[NotificationScheduler] 10일 경과 알림 삭제 완료 - cutoff: {}", cutoff);
    }
}
