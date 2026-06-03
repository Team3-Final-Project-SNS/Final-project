package com.example.team3final.domain.meet.scheduler;

import com.example.team3final.domain.meet.repository.MeetVerificationRepository;
import com.example.team3final.domain.meet.util.MeetRedisZSetKeys;
import com.example.team3final.domain.notification.service.NotificationPublisher;
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
public class ExtensionTimeoutScheduler {

    private final StringRedisTemplate redisTemplate;
    private final MeetVerificationRepository meetVerificationRepository;
    private final NotificationPublisher notificationPublisher;
    private final DefaultRedisScript<List<String>> popReadyItemsScript; // RedisConfig Bean 주입

    // 한국 시간대 오프셋
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    // 1분마다 실행 → ZSet에서 타임아웃된 연장 요청 꺼내서 EXPIRED 처리
    // fixedDelay: 이전 실행 완료 후 1분 뒤 실행 (동시 실행 방지)
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void expireTimeoutExtensions() {
        log.debug("[ExtensionTimeoutScheduler] 연장 요청 타임아웃 체크 실행");

        // 현재 시각 Unix Timestamp — ZSet score 비교에 사용
        long nowScore = LocalDateTime.now().toEpochSecond(KST);

        // Lua Script로 원자적 처리 (조회 + 삭제 동시에)
        // → 서버 여러 대여도 중복 처리 없음
        List<String> meetVerificationIds = redisTemplate.execute(
                popReadyItemsScript,
                List.of(MeetRedisZSetKeys.EXTENSION_TIMEOUT), // KEYS[1]
                String.valueOf(nowScore)                      // ARGV[1]
        );

        if (meetVerificationIds.isEmpty()) {
            return;
        }

        log.info("[ExtensionTimeoutScheduler] 연장 타임아웃 처리 대상: {}건", meetVerificationIds.size());

        for (String idStr : meetVerificationIds) {
            Long meetVerificationId = Long.parseLong(idStr);

            // meetVerificationId로 MeetVerification 조회
            meetVerificationRepository.findById(meetVerificationId).ifPresent(mv -> {

                // 이미 수락/거절/만료된 건 스킵 (중복 처리 방지)
                if (!mv.isExtensionRequested()) {
                    return;
                }

                // EXPIRED 처리
                mv.expireExtension();

                // 만료 알림 발송
                notificationPublisher.sendMeetExtendExpired(mv.getExtensionRequesterId(), mv.getMatchId());
            });
        }
    }
}