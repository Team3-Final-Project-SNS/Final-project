package com.example.team3final.domain.review.scheduler;

import com.example.team3final.domain.match.service.MatchInternalService;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.review.repository.ReviewRepository;
import com.example.team3final.domain.review.util.ReviewRedisZSetKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewDeadlineReminderScheduler {

    private final StringRedisTemplate redisTemplate;
    private final MatchInternalService matchInternalService;
    private final ReviewRepository reviewRepository;
    private final NotificationPublisher notificationPublisher;
    private final DefaultRedisScript<List<String>> popReadyItemsScript; // RedisConfig Bean 주입

    // 한국 시간대 오프셋 — Unix Timestamp 변환 시 KST(UTC+9) 기준 적용
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    // 1분마다 실행 - 후기 작성 마지막 날 알림 예약 시간이 지난 matchId 처리
    // fixedDelay: 이전 실행 완료 후 1분 뒤 실행 (동시 실행 방지)
    @Scheduled(fixedDelay = 60000)
    @Transactional(readOnly = true)
    public void sendReviewDeadlineReminders() {

        // 현재 시각 Unix Timestamp — ZSet score 비교에 사용
        long nowScore = LocalDateTime.now().toEpochSecond(KST);

        // Lua Script로 원자적 처리 (조회 + 삭제 동시에)
        // → 서버 여러 대여도 중복 처리 없음
        List<String> matchIds = redisTemplate.execute(
                popReadyItemsScript,
                List.of(ReviewRedisZSetKeys.DEADLINE_REMINDER), // KEYS[1]
                String.valueOf(nowScore)                        // ARGV[1]
        );

        if (matchIds.isEmpty()) {
            return;
        }

        log.info("[ReviewDeadlineReminderScheduler] 후기 작성 마지막 날 알림 대상: {}건", matchIds.size());

        for (String idStr : matchIds) {
            Long matchId = Long.parseLong(idStr);

            matchInternalService.findCompletedMatchById(matchId).ifPresent(match -> {
                // 이미 후기를 작성한 신청자는 스킵
                if (reviewRepository.existsByMatchIdAndWriterId(match.getId(), match.getApplicantId())) {
                    return;
                }

                // 10. 후기 작성 마지막 날 알림 - 미작성 신청자에게
                notificationPublisher.sendReviewDeadlineReminder(match.getApplicantId(), match.getId());
            });
        }
    }
}