package com.example.team3final.domain.meet.service;

import com.example.team3final.domain.meet.util.MeetRedisZSetKeys;
import com.example.team3final.domain.meet.service.support.MeetVerificationPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeetOverdueReservationService {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private final StringRedisTemplate redisTemplate;

    // 연장된 만남 시각을 기준으로 HOST와 모든 활성 GUEST의
    // 기존 10분 경과 알림 예약 시간을 갱신한다.
    // 현재 10분 경과 알림은 발송하지 않고 drain하지만, 기존 Redis 예약 정합성을 위해 같은 기준값을 유지한다.
    public void rescheduleAfterExtension(
            Long postId,
            List<Long> activeMatchIds,
            LocalDateTime extendedMeetAt
    ) {
        LocalDateTime now = LocalDateTime.now();
        String postMember = postId.toString();

        updateOrRemoveReservation(MeetRedisZSetKeys.REMINDER_30_HOST, postMember, extendedMeetAt.minusMinutes(30), now);
        updateOrRemoveReservation(MeetRedisZSetKeys.REMINDER_15_HOST, postMember, extendedMeetAt.minusMinutes(15), now);
        updateOrRemoveReservation(MeetRedisZSetKeys.REMINDER_IMMINENT_HOST, postMember, extendedMeetAt.minusMinutes(5), now);
        updateOrRemoveReservation(
                MeetRedisZSetKeys.REMINDER_OVERDUE_HOST,
                postMember,
                extendedMeetAt.plusMinutes(MeetVerificationPolicy.NO_SHOW_JUDGE_MINUTES),
                now
        );

        for (Long matchId : activeMatchIds) {
            String matchMember = matchId.toString();
            updateOrRemoveReservation(MeetRedisZSetKeys.REMINDER_30_GUEST, matchMember, extendedMeetAt.minusMinutes(30), now);
            updateOrRemoveReservation(MeetRedisZSetKeys.REMINDER_15_GUEST, matchMember, extendedMeetAt.minusMinutes(15), now);
            updateOrRemoveReservation(MeetRedisZSetKeys.REMINDER_IMMINENT_GUEST, matchMember, extendedMeetAt.minusMinutes(5), now);
            updateOrRemoveReservation(
                    MeetRedisZSetKeys.REMINDER_OVERDUE_GUEST,
                    matchMember,
                    extendedMeetAt.plusMinutes(MeetVerificationPolicy.NO_SHOW_JUDGE_MINUTES),
                    now
            );
        }
    }

    // 같은 key/member로 ZADD하면 기존 예약의 score가 새 시각으로 갱신된다.
    // Redis 장애가 DB 연장 처리를 방해하지 않도록 실패는 로그로 남긴다.
    public boolean updateReservation(
            String key,
            String member,
            LocalDateTime targetAt
    ) {
        try {
            double score = targetAt.toEpochSecond(KST);
            redisTemplate.opsForZSet().add(key, member, score);
            return true;
        } catch (RuntimeException e) {
            log.error(
                    "[MeetOverdueReservation] Redis 예약 실패 key={}, member={}, targetAt={}",
                    key,
                    member,
                    targetAt,
                    e
            );
            return false;
        }
    }
    private void updateOrRemoveReservation(
            String key,
            String member,
            LocalDateTime targetAt,
            LocalDateTime now
    ) {
        if (now.isBefore(targetAt)) {
            updateReservation(key, member, targetAt);
            return;
        }
        redisTemplate.opsForZSet().remove(key, member);
    }
}
