package com.example.team3final.domain.dispute.scheduler;

import com.example.team3final.domain.dispute.repository.DisputeRepository;
import com.example.team3final.domain.dispute.util.DisputeRedisZSetKeys;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class DisputeScheduler {

    private final StringRedisTemplate redisTemplate;
    private final NotificationPublisher notificationPublisher;
    private final DisputeRepository disputeRepository;

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    // 이의제기 추가 증빙 마감 임박 알림 - 1분마다 실행
    // HOLD 판정 시 ZSet에 holdAt+23h score로 등록해둔 disputeId를 꺼내서 알림 발송
    @Scheduled(fixedDelay = 60000)
    public void sendDisputeDeadlineReminders() {

        // 현재 시각을 Unix Timestamp(score)로 변환
        double nowScore = LocalDateTime.now().toEpochSecond(KST);

        // ZSet에서 score가 현재 시각 이하인 항목 꺼내기
        // holdAt+23h가 지난 이의제기 건만 조회
        Set<String> disputeIds = redisTemplate.opsForZSet()
                .rangeByScore(DisputeRedisZSetKeys.DEADLINE_REMINDER, 0, nowScore);

        if (disputeIds == null || disputeIds.isEmpty()) return;

        log.info("[DisputeScheduler] 마감 임박 알림 대상: {}건", disputeIds.size());

        for (String idStr : disputeIds) {
            Long disputeId = Long.parseLong(idStr);

            // submitterId 조회 후 알림 발송
            disputeRepository.findById(disputeId).ifPresent(dispute ->
                    notificationPublisher.sendDisputeDeadlineReminder(
                            dispute.getSubmitterId(), // 이의제기 신청자
                            disputeId
                    )
            );

            // 발송 완료 후 ZSet에서 제거 (중복 발송 방지)
            redisTemplate.opsForZSet().remove(DisputeRedisZSetKeys.DEADLINE_REMINDER, idStr);
        }
    }
}