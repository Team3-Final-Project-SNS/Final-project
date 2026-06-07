package com.example.team3final.domain.meet.scheduler;

import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.service.MatchService;
import com.example.team3final.domain.meet.util.MeetRedisZSetKeys;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.service.PostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MeetReminderScheduler {

    private final StringRedisTemplate redisTemplate;
    private final NotificationPublisher notificationPublisher;
    private final MatchService matchService;   // matchId → applicantId, postId 조회
    private final PostService postService;     // postId → authorId 조회

    // Lua Script - ZSet 조회 + 삭제 원자적 처리
    // 서버 여러 대여도 중복 처리 없음
    private final DefaultRedisScript<List<String>> popReadyItemsScript;

    // 한국 시간대 오프셋
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    // 만남 30분 전 알림 - 1분마다 실행
    @Scheduled(fixedDelay = 60000)
    public void sendReminder30() {
        // ZSet에서 현재 시각 이전 matchId 꺼내서 30분 전 알림 발송
        List<String> matchIds = popReadyItems(MeetRedisZSetKeys.REMINDER_30);

        if (matchIds.isEmpty()) return;

        log.info("[MeetReminderScheduler] 30분 전 알림 대상: {}건", matchIds.size());

        for (String matchIdStr : matchIds) {
            Long matchId = Long.parseLong(matchIdStr);
            MatchInfoDto matchInfo = matchService.getMatchInfo(matchId);
            Long authorId = postService.getPostById(matchInfo.postId()).getAuthorId();

            // 5. 만남 30분 전 알림 - 만남 참여자 모두에게
            notificationPublisher.sendMeetReminder30(authorId, matchId);
            notificationPublisher.sendMeetReminder30(matchInfo.applicantId(), matchId);
        }
    }

    // 만남 15분 전 알림 - 1분마다 실행
    @Scheduled(fixedDelay = 60000)
    public void sendReminder15() {
        List<String> matchIds = popReadyItems(MeetRedisZSetKeys.REMINDER_15);

        if (matchIds.isEmpty()) return;

        log.info("[MeetReminderScheduler] 15분 전 알림 대상: {}건", matchIds.size());

        for (String matchIdStr : matchIds) {
            Long matchId = Long.parseLong(matchIdStr);
            MatchInfoDto matchInfo = matchService.getMatchInfo(matchId);
            Long authorId = postService.getPostById(matchInfo.postId()).getAuthorId();

            // 6. 만남 15분 전 알림 - 만남 참여자 모두에게
            notificationPublisher.sendMeetReminder15(authorId, matchId);
            notificationPublisher.sendMeetReminder15(matchInfo.applicantId(), matchId);
        }
    }

    // 만남 임박 알림 (5분 전) - 1분마다 실행
    @Scheduled(fixedDelay = 60000)
    public void sendImminent() {
        List<String> matchIds = popReadyItems(MeetRedisZSetKeys.REMINDER_IMMINENT);

        if (matchIds.isEmpty()) return;

        log.info("[MeetReminderScheduler] 임박 알림 대상: {}건", matchIds.size());

        for (String matchIdStr : matchIds) {
            Long matchId = Long.parseLong(matchIdStr);
            MatchInfoDto matchInfo = matchService.getMatchInfo(matchId);
            Long authorId = postService.getPostById(matchInfo.postId()).getAuthorId();

            // 7. 만남 5분 전 임박 알림 - 만남 참여자 모두에게
            notificationPublisher.sendMeetImminent(authorId, matchId);
            notificationPublisher.sendMeetImminent(matchInfo.applicantId(), matchId);
        }
    }

    // 만남 시간 10분 경과 알림 - 1분마다 실행
// sendImminent()와 동일한 패턴: Redis ZSet에서 처리 대상 꺼내서 양측에게 발송
    @Scheduled(fixedDelay = 60000)
    public void sendOverdue() {
        // REMINDER_OVERDUE ZSet에서 현재 시각 이전 matchId 꺼내기
        List<String> matchIds = popReadyItems(MeetRedisZSetKeys.REMINDER_OVERDUE);

        if (matchIds.isEmpty()) return;

        log.info("[MeetReminderScheduler] 10분 경과 알림 대상: {}건", matchIds.size());

        for (String matchIdStr : matchIds) {
            Long matchId = Long.parseLong(matchIdStr);
            MatchInfoDto matchInfo = matchService.getMatchInfo(matchId);

            // postId로 게시글 작성자(HOST) ID 조회
            Long authorId = postService.getPostById(matchInfo.postId()).getAuthorId();

            // 8. 만남 시간 10분 경과 알림 - 만남 참여자 모두에게
            notificationPublisher.sendMeetOverdue(authorId, matchId);
            notificationPublisher.sendMeetOverdue(matchInfo.applicantId(), matchId);
        }
    }

    // ZSet에서 현재 시각 이전 항목 원자적으로 꺼내기
    // Lua Script로 조회 + 삭제 동시에 처리 → 중복 발송 방지
    private List<String> popReadyItems(String zSetKey) {
        // 현재 시각을 Unix Timestamp(숫자)로 변환 — ZSet score 비교에 사용
        long nowScore = LocalDateTime.now().toEpochSecond(KST);

        // Lua Script 실행
        // KEYS[1] = ZSet 키 (어느 ZSet에서 꺼낼지)
        // ARGV[1] = 현재 Unix Timestamp (이 값 이하 score 항목만 꺼냄)
        // 반환값: 처리 대상 matchId 목록
        return redisTemplate.execute(
                popReadyItemsScript, // 앱 시작 시 1회 파싱된 Lua Script Bean
                List.of(zSetKey),    // KEYS[1]
                String.valueOf(nowScore) // ARGV[1]
        );
    }
}