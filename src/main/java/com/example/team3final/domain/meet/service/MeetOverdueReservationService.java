package com.example.team3final.domain.meet.service;

import com.example.team3final.domain.meet.util.MeetRedisZSetKeys;
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
    // 10분 경과 알림 예약 시간을 갱신한다.
    public void rescheduleAfterExtension(
            Long postId,
            List<Long> activeMatchIds,
            LocalDateTime extendedMeetAt
    ) {
        LocalDateTime overdueAt = extendedMeetAt.plusMinutes(10);

        // HOST 알림은 postId를 Redis member로 사용한다.
        updateReservation(
                MeetRedisZSetKeys.REMINDER_OVERDUE_HOST,
                postId.toString(),
                overdueAt
        );

        // GUEST 알림은 각 신청자의 matchId를 Redis member로 사용한다.
        for (Long matchId : activeMatchIds) {
            updateReservation(
                    MeetRedisZSetKeys.REMINDER_OVERDUE_GUEST,
                    matchId.toString(),
                    overdueAt
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
}
