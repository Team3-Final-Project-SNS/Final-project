package com.example.team3final.domain.meet.scheduler;

import com.example.team3final.domain.match.dto.response.MatchInfoDto;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.service.MatchInternalService;
import com.example.team3final.domain.meet.entity.MeetVerification;
import com.example.team3final.domain.meet.enums.VerificationStatus;
import com.example.team3final.domain.meet.repository.MeetVerificationRepository;
import com.example.team3final.domain.meet.service.MeetOverdueReservationService;
import com.example.team3final.domain.meet.util.MeetRedisZSetKeys;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.service.PostInternalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MeetReminderScheduler {

    private final StringRedisTemplate redisTemplate;
    private final NotificationPublisher notificationPublisher;
    private final MatchInternalService matchInternalService;
    private final PostInternalService postInternalService;
    private final MeetVerificationRepository meetVerificationRepository;
    private final MeetOverdueReservationService meetOverdueReservationService;

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
            // 10분 경과 알림 대상 postId들을 순회하며 HOST(등록자)에게 알림 발송
            for (String postIdStr : postIds) {
                // ZSet에서 꺼낸 값은 문자열이므로 Long으로 변환
                Long postId = Long.parseLong(postIdStr);

                // postId로 게시글 조회 → 등록자(authorId) 정보 필요
                Post post = postInternalService.getPostById(postId);

                // postId에 연결된 활성(MATCHED) 매칭의 matchId 조회
                // 그룹 만남의 모든 활성 matchId 기준으로 HOST 알림 대상 판단
                List<Long> activeMatchIds = matchInternalService.getActiveMatchIdsByPostId(postId);

                // 활성 매칭이 없으면(이미 취소됨) 알림 보낼 대상 없음 → 스킵
                if (activeMatchIds.isEmpty()) {
                    log.warn("[MeetReminderScheduler] 10분 경과 HOST 알림 스킵 - 활성 매칭 없음 postId: {}", postId);
                    continue;
                }

                // 발송 직전 인증 상태를 확인해 이미 장소 또는 QR 인증을 마친 사용자에게 오발송하지 않는다.
                // 첫 번째 match만 보지 않고, HOST 장소 인증이 필요한 참여자 row 선택
                Optional<MeetVerification> meetVerification =
                        meetVerificationRepository.findAllByMatchIdIn(activeMatchIds).stream()
                                .filter(this::shouldSendHostOverdue)
                                .findFirst();

                if (meetVerification.isEmpty()) {
                    log.warn(
                            "[MeetReminderScheduler] 10분 경과 HOST 알림 스킵"
                                    + " - 만남 인증 정보 없음 matchId: {}",
                            postId
                    );
                    continue;
                }

                if (!shouldSendHostOverdue(meetVerification.get())) {
                    log.info(
                            "[MeetReminderScheduler] 10분 경과 HOST 알림 스킵"
                                    + " - 인증 완료 또는 발송 대상 아님 matchId: {}, status: {}",
                            meetVerification.get().getMatchId(),
                            meetVerification.get().getStatus()
                    );
                    continue;
                }

                LocalDateTime effectiveMeetAt =
                        getEffectiveMeetAt(meetVerification.get(), post.getMeetAt());

                // 기존 예약 pop과 연장 재예약이 경합했으면 실제 발송 시각으로 다시 예약한다.
                if (requeueAndSkipIfEarly(
                        MeetRedisZSetKeys.REMINDER_OVERDUE_HOST,
                        postIdStr,
                        effectiveMeetAt,
                        LocalDateTime.now()
                )) {
                    continue;
                }

                // relatedId로 matchId를 전달 → 알림 클릭 시 올바른 매칭 화면으로 이동
                notificationPublisher.sendMeetOverdue(post.getAuthorId(), meetVerification.get().getMatchId());
            }
        }

        // GUEST: matchId 기준으로 신청자별 각각 발송
        List<String> matchIds = popReadyItems(MeetRedisZSetKeys.REMINDER_OVERDUE_GUEST);
        if (!matchIds.isEmpty()) {
            log.info("[MeetReminderScheduler] 10분 경과 GUEST 알림 대상: {}건", matchIds.size());
            for (String matchIdStr : matchIds) {
                Long matchId = Long.parseLong(matchIdStr);
                MatchInfoDto matchInfo = matchInternalService.getMatchInfo(matchId);
                if (matchInfo.status() != MatchStatus.MATCHED) {
                    log.info("[MeetReminderScheduler] 10분 경과 GUEST 알림 스킵 - 비활성 매칭 matchId: {}, status: {}",
                            matchId, matchInfo.status());
                    continue;
                }
                Post post = postInternalService.getPostById(matchInfo.postId());

                // QR 인증까지 완료된 DONE 상태와 장소 인증이 끝난 VERIFIED 상태는 발송 대상이 아니다.
                Optional<MeetVerification> meetVerification =
                        meetVerificationRepository.findByMatchId(matchId);

                if (meetVerification.isEmpty()) {
                    log.warn(
                            "[MeetReminderScheduler] 10분 경과 GUEST 알림 스킵"
                                    + " - 만남 인증 정보 없음 matchId: {}",
                            matchId
                    );
                    continue;
                }

                if (!shouldSendGuestOverdue(meetVerification.get())) {
                    log.info(
                            "[MeetReminderScheduler] 10분 경과 GUEST 알림 스킵"
                                    + " - 인증 완료 또는 발송 대상 아님 matchId: {}, status: {}",
                            matchId,
                            meetVerification.get().getStatus()
                    );
                    continue;
                }

                LocalDateTime effectiveMeetAt =
                        getEffectiveMeetAt(meetVerification.get(), post.getMeetAt());

                // 아직 실제 경과 시각 전이면 같은 matchId를 올바른 시각으로 다시 예약한다.
                if (requeueAndSkipIfEarly(
                        MeetRedisZSetKeys.REMINDER_OVERDUE_GUEST,
                        matchIdStr,
                        effectiveMeetAt,
                        LocalDateTime.now()
                )) {
                    continue;
                }

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
            Post post = postInternalService.getPostById(postId);
            LocalDateTime effectiveMeetAt = meetVerificationRepository.findEffectiveExtendedMeetAtByPostId(postId)
                    .orElse(post.getMeetAt());

            // 스킵 조건 체크
            LocalDateTime skipBoundary = skipIfAfterMinutes > 0
                    ? effectiveMeetAt.minusMinutes(skipIfAfterMinutes)
                    : effectiveMeetAt;

            if (LocalDateTime.now().isAfter(skipBoundary)) {
                log.info("[MeetReminderScheduler] {} HOST 알림 스킵 - postId: {}", label, postId);
                continue;
            }

            // postId에 연결된 활성(MATCHED) 매칭의 matchId를 조회
            // (CANCELLED/COMPLETED 매칭은 자동 제외되고, 그룹 매칭이어도 항상 동일한 매칭 선택됨)
            // HOST 리마인드는 만남 단위 1회 발송, relatedId는 대표 matchId 사용
            List<Long> activeMatchIds = matchInternalService.getActiveMatchIdsByPostId(postId);

            if (activeMatchIds.isEmpty()) {
                log.warn("[MeetReminderScheduler] {} HOST 알림 스킵 - 활성 매칭 없음 postId: {}", label, postId);
                continue;
            }

            notifier.send(post.getAuthorId(), activeMatchIds.get(0));
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
            MatchInfoDto matchInfo = matchInternalService.getMatchInfo(matchId);

            if (matchInfo.status() != MatchStatus.MATCHED) {
                log.info("[MeetReminderScheduler] {} GUEST 알림 스킵 - 비활성 매칭 matchId: {}, status: {}",
                        label, matchId, matchInfo.status());
                continue;
            }

            Post post = postInternalService.getPostById(matchInfo.postId());
            LocalDateTime effectiveMeetAt = meetVerificationRepository.findEffectiveExtendedMeetAtByMatchId(matchId)
                    .orElse(post.getMeetAt());

            // 스킵 조건 체크
            LocalDateTime skipBoundary = skipIfAfterMinutes > 0
                    ? effectiveMeetAt.minusMinutes(skipIfAfterMinutes)
                    : effectiveMeetAt;

            if (LocalDateTime.now().isAfter(skipBoundary)) {
                log.info("[MeetReminderScheduler] {} GUEST 알림 스킵 - matchId: {}", label, matchId);
                continue;
            }

            notifier.send(matchInfo.applicantId(), matchId);
        }
    }

    // 연장된 시각이 있으면 extendedMeetAt을 사용하고,
    // 연장하지 않았으면 최초 Post.meetAt을 사용한다.
    private LocalDateTime getEffectiveMeetAt(
            MeetVerification meetVerification,
            LocalDateTime originalMeetAt
    ) {
        return meetVerification.getExtendedMeetAt() != null
                ? meetVerification.getExtendedMeetAt()
                : originalMeetAt;
    }

    private boolean shouldSendHostOverdue(MeetVerification meetVerification) {
        // 10분 경과 알림은 아직 GPS 장소 인증이 필요한 PENDING 상태에만 발송한다.
        // VERIFIED와 DONE 상태는 장소 인증이 끝났으므로 알림 대상에서 제외한다.
        return meetVerification.getStatus() == VerificationStatus.PENDING
                && !meetVerification.isAuthorPlaceVerified();
    }

    private boolean shouldSendGuestOverdue(MeetVerification meetVerification) {
        // 한쪽만 장소 인증한 경우에도 이미 인증한 신청자에게는 알림을 보내지 않는다.
        return meetVerification.getStatus() == VerificationStatus.PENDING
                && !meetVerification.isApplicantPlaceVerified();
    }

    // Redis 항목이 DB 기준 실제 발송 시각보다 일찍 pop됐는지 확인한다.
    // 아직 이르면 올바른 시각으로 재등록하고 이번 발송은 건너뛴다.
    private boolean requeueAndSkipIfEarly(
            String key,
            String member,
            LocalDateTime effectiveMeetAt,
            LocalDateTime now
    ) {
        LocalDateTime actualOverdueAt = effectiveMeetAt.plusMinutes(10);

        // 실제 발송 시각과 같거나 이후면 정상 발송한다.
        if (!now.isBefore(actualOverdueAt)) {
            return false;
        }

        boolean requeued = meetOverdueReservationService.updateReservation(
                key,
                member,
                actualOverdueAt
        );

        if (requeued) {
            log.info(
                    "[MeetReminderScheduler] 이른 10분 경과 알림 재예약 key={}, member={}, targetAt={}",
                    key,
                    member,
                    actualOverdueAt
            );
        } else {
            // 재등록 실패 시 이른 알림을 잘못 발송하지 않고 운영 확인을 위한 오류 로그를 남긴다.
            log.error(
                    "[MeetReminderScheduler] 이른 10분 경과 알림 재예약 실패 key={}, member={}, targetAt={}",
                    key,
                    member,
                    actualOverdueAt
            );
        }

        // 아직 이른 경우에는 재등록 성공 여부와 관계없이 발송하지 않는다.
        return true;
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
