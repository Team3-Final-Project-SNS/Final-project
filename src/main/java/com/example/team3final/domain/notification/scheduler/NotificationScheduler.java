package com.example.team3final.domain.notification.scheduler;

import com.example.team3final.domain.notification.repository.NotificationRepository;
import com.example.team3final.domain.notification.service.NotificationCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final NotificationRepository notificationRepository;
    private final NotificationCacheService notificationCacheService;

    /**
     * Deletes notifications older than 10 days.
     * Runs once a day at midnight and removes rows in chunks to reduce DB load.
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void deleteOldNotifications() {

        LocalDateTime cutoff = LocalDateTime.now().minusDays(10);

        int chunkSize = 1000;
        int totalDeletedCount = 0;

        while (true) {
            List<Long> oldNotificationIds = notificationRepository.findOldNotificationIds(
                    cutoff,
                    PageRequest.of(0, chunkSize)
            );

            if (oldNotificationIds.isEmpty()) {
                break;
            }

            notificationRepository.deleteAllByIdInBatch(oldNotificationIds);
            totalDeletedCount += oldNotificationIds.size();

            if (oldNotificationIds.size() < chunkSize) {
                break;
            }
        }

        if (totalDeletedCount > 0) {
            notificationCacheService.evictAll();
        }

        log.info("[NotificationScheduler] Deleted old notifications - cutoff: {}, deletedCount: {}",
                cutoff, totalDeletedCount);
    }
}
