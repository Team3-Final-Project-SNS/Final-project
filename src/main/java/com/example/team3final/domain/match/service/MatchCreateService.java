package com.example.team3final.domain.match.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.MatchException;
import com.example.team3final.domain.match.dto.response.CreateMatchResponseDto;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class MatchCreateService {

    private final PostService postService;
    private final RedissonClient redissonClient;
    private final NotificationPublisher notificationPublisher;
    private final MatchTransactionService matchTransactionService; // 트랜잭션 위임 대상

    @Value("${match.lock.group-wait-ms:500}")
    private long groupLockWaitMs;

    private static final String MATCH_LOCK_KEY = "match:lock:post:";
    private static final long REDIS_LOCK_LEASE_SECONDS = 5;

    // ====================================================================
    // createMatch() — 락 관리 전담, @Transactional 없음
    //
    // [락 원칙] Redis 락은 반드시 트랜잭션 바깥에서 잡아야 함
    //   흐름: 락 획득 → 트랜잭션 시작(외부 빈) → DB 작업 → 커밋 → 락 해제
    //
    // [알림 발송] 락 해제 후 비동기(@Async)로 발송
    //   Kafka 발송 지연이 락 점유 시간에 포함되지 않도록 분리
    // ====================================================================
    public CreateMatchResponseDto createMatch(Long postId, Long applicantId) {

        String lockKey = MATCH_LOCK_KEY + postId;
        RLock lock = redissonClient.getLock(lockKey);

        boolean acquired = false;
        CreateMatchResponseDto result = null;

        try {
            Post post = postService.getPostById(postId);

            // 1:1 매칭: 즉시 실패(waitTime=0), 단체 매칭: 설정값만큼 대기
            long waitTime = (post.getMaxApplicants() == 2) ? 0L : groupLockWaitMs;

            acquired = lock.tryLock(waitTime, REDIS_LOCK_LEASE_SECONDS * 1000, TimeUnit.MILLISECONDS);

            if (!acquired) {
                throw new MatchException(ErrorCode.MATCH_ALREADY_MATCHED);
            }

            // 락 획득 후 MatchTransactionService(외부 빈)에 DB 작업 위임
            result = matchTransactionService.createMatchInTransaction(postId, applicantId);
            return result;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MatchException(ErrorCode.MATCH_ALREADY_MATCHED);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
            // 락 해제 후 알림 발송 (비동기 @Async → 즉시 리턴, 논블로킹)
            if (result != null) {
                notificationPublisher.sendMatchApplied(result.authorId(), result.matchId());
                notificationPublisher.sendMatchConfirmed(result.applicantId(), result.matchId());
            }
        }
    }
}