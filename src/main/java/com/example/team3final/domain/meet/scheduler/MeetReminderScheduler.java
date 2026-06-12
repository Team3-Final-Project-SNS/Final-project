package com.example.team3final.domain.meet.scheduler;

import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.service.MatchService;
import com.example.team3final.domain.meet.util.MeetRedisZSetKeys;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.entity.Post;
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
    private final MatchService matchService;
    private final PostService postService;

    // Lua Script - ZSet 조회 + 삭제 원자적 처리 (중복 발송 방지)
    private final DefaultRedisScript<List<String>> popReadyItemsScript;

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    // ── 30분 전 알림 ─────────────────────────────────────────────────────

    @Scheduled(fixedDelay = 60000)
    public void sendReminder30() {
        // HOST: postId 기준으로 등록자에게 1번만 발송
        // 이미 15분 전 시점이 지났으면 스킵 (너무 늦게 처리된 경우 방어)
        sendHostReminder(
                MeetRedisZSetKeys.REMINDER_30_HOST,
                "30분 전",
                15,
                (authorId, matchId) -> notificationPublisher.sendMeetReminder30(authorId, matchId)
        );

        // GUEST: matchId 기준으로 신청자별 각각 발송
        sendGuestReminder(
                MeetRedisZSetKeys.REMINDER_30_GUEST,
                "30분 전",
                15,
                (applicantId, matchId) -> notificationPublisher.sendMeetReminder30(applicantId, matchId)
        );
    }

    // ── 15분 전 알림 ─────────────────────────────────────────────────────

    @Scheduled(fixedDelay = 60000)
    public void sendReminder15() {
        sendHostReminder(
                MeetRedisZSetKeys.REMINDER_15_HOST,
                "15분 전",
                5,
                (authorId, matchId) -> notificationPublisher.sendMeetReminder15(authorId, matchId)
        );

        sendGuestReminder(
                MeetRedisZSetKeys.REMINDER_15_GUEST,
                "15분 전",
                5,
                (applicantId, matchId) -> notificationPublisher.sendMeetReminder15(applicantId, matchId)
        );
    }

    // ── 임박 알림 (5분 전) ───────────────────────────────────────────────

    @Scheduled(fixedDelay = 60000)
    public void sendImminent() {
        // skipIfAfterMinutes = 0: 만남 시각 자체가 지났으면 스킵
        sendHostReminder(
                MeetRedisZSetKeys.REMINDER_IMMINENT_HOST,
                "임박",
                0,
                (authorId, matchId) -> notificationPublisher.sendMeetImminent(authorId, matchId)
        );

        sendGuestReminder(
                MeetRedisZSetKeys.REMINDER_IMMINENT_GUEST,
                "임박",
                0,
                (applicantId, matchId) -> notificationPublisher.sendMeetImminent(applicantId, matchId)
        );
    }

    // ── 10분 경과 알림 ───────────────────────────────────────────────────

    @Scheduled(fixedDelay = 60000)
    public void sendOverdue() {
        // HOST: postId 기준으로 등록자에게 1번만 발송
        List<String> postIds = popReadyItems(MeetRedisZSetKeys.REMINDER_OVERDUE_HOST);
        if (!postIds.isEmpty()) {
            log.info("[MeetReminderScheduler] 10분 경과 HOST 알림 대상: {}건", postIds.size());
            for (String postIdStr : postIds) {
                Long postId = Long.parseLong(postIdStr);
                Post post = postService.getPostById(postId);
                notificationPublisher.sendMeetOverdue(post.getAuthorId(), postId);
            }
        }

        // GUEST: matchId 기준으로 신청자별 각각 발송
        List<String> matchIds = popReadyItems(MeetRedisZSetKeys.REMINDER_OVERDUE_GUEST);
        if (!matchIds.isEmpty()) {
            log.info("[MeetReminderScheduler] 10분 경과 GUEST 알림 대상: {}건", matchIds.size());
            for (String matchIdStr : matchIds) {
                Long matchId = Long.parseLong(matchIdStr);
                MatchInfoDto matchInfo = matchService.getMatchInfo(matchId);
                notificationPublisher.sendMeetOverdue(matchInfo.applicantId(), matchId);
            }
        }
    }

    // ── 공통 헬퍼 ────────────────────────────────────────────────────────

    // HOST(등록자) 알림 발송 — postId 단위
    // skipIfAfterMinutes: 만남 시각 기준으로 이 분 전이 지났으면 스킵
    //   ex) 30분 전 알림인데 이미 15분 전 시점이 지났으면 → 스킵
    //   0이면 만남 시각 자체가 지났을 때만 스킵
    private void sendHostReminder(String zSetKey, String label,
                                  int skipIfAfterMinutes, HostNotifier notifier) {
        List<String> postIds = popReadyItems(zSetKey);
        if (postIds.isEmpty()) return;

        log.info("[MeetReminderScheduler] {} HOST 알림 대상: {}건", label, postIds.size());

        for (String postIdStr : postIds) {
            Long postId = Long.parseLong(postIdStr);
            Post post = postService.getPostById(postId);

            // 스킵 조건 체크
            LocalDateTime skipBoundary = skipIfAfterMinutes > 0
                    ? post.getMeetAt().minusMinutes(skipIfAfterMinutes)
                    : post.getMeetAt();

            if (LocalDateTime.now().isAfter(skipBoundary)) {
                log.info("[MeetReminderScheduler] {} HOST 알림 스킵 - postId: {}", label, postId);
                continue;
            }

            // postId → matchId 변환
            List<Long> matchIds = matchService.getMatchIdsByPostId(postId);

            // 매칭이 없으면 아직 신청자가 없는 게시글이므로 알림 발송 스킵
            if (matchIds.isEmpty()) {
                log.warn("[MeetReminderScheduler] {} HOST 알림 스킵 - matchId 없음 postId: {}", label, postId);
                continue;
            }

            // 1:1 매칭이므로 첫 번째 matchId 사용
            Long matchId = matchIds.get(0);
            notifier.send(post.getAuthorId(), matchId);
        }
    }

    // GUEST(신청자) 알림 발송 — matchId 단위
    private void sendGuestReminder(String zSetKey, String label,
                                   int skipIfAfterMinutes, GuestNotifier notifier) {
        List<String> matchIds = popReadyItems(zSetKey);
        if (matchIds.isEmpty()) return;

        log.info("[MeetReminderScheduler] {} GUEST 알림 대상: {}건", label, matchIds.size());

        for (String matchIdStr : matchIds) {
            Long matchId = Long.parseLong(matchIdStr);
            MatchInfoDto matchInfo = matchService.getMatchInfo(matchId);
            Post post = postService.getPostById(matchInfo.postId());

            // 스킵 조건 체크
            LocalDateTime skipBoundary = skipIfAfterMinutes > 0
                    ? post.getMeetAt().minusMinutes(skipIfAfterMinutes)
                    : post.getMeetAt();

            if (LocalDateTime.now().isAfter(skipBoundary)) {
                log.info("[MeetReminderScheduler] {} GUEST 알림 스킵 - matchId: {}", label, matchId);
                continue;
            }

            notifier.send(matchInfo.applicantId(), matchId);
        }
    }

    // ZSet에서 현재 시각 이전 항목 원자적으로 꺼내기
    // Lua Script로 조회 + 삭제 동시에 처리 → 중복 발송 방지
    private List<String> popReadyItems(String zSetKey) {
        long nowScore = LocalDateTime.now().toEpochSecond(KST);
        return redisTemplate.execute(
                popReadyItemsScript,
                List.of(zSetKey),
                String.valueOf(nowScore)
        );
    }

    // ── 함수형 인터페이스 ─────────────────────────────────────────────────

    // HOST 알림 발송: (authorId, matchId)
    @FunctionalInterface
    private interface HostNotifier {
        void send(Long authorId, Long matchId);
    }

    // GUEST 알림 발송: (applicantId, matchId)
    @FunctionalInterface
    private interface GuestNotifier {
        void send(Long applicantId, Long matchId);
    }
}