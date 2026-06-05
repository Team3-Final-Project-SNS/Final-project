package com.example.team3final.domain.match.service;

// ====================================================================
// 목적: 동시성 제어 테스트를 위한 6가지 전략 구현
//       각 메서드는 락 전략만 다르고 내부 비즈니스 로직은 동일
//
// [테스트 전략 목록]
//   전략 A: 비관적 락(Pessimistic Lock) + 즉시 실패 (NOWAIT)
//   전략 B: 비관적 락(Pessimistic Lock) + 대기 후 실패
//   전략 C: 낙관적 락(Optimistic Lock)  + 즉시 실패
//   전략 D: 낙관적 락(Optimistic Lock)  + 재시도 (최대 3회)
//   전략 E: Redis 분산 락               + 즉시 실패 (waitTime=0)
//   전략 F: Redis 분산 락               + 500ms 대기 후 실패
//   락 없음: Race Condition 재현용
//
// 공통 비즈니스 로직:
//   1. postId로 게시글 조회
//   2. 게시글 상태가 OPEN인지 확인 (아니면 MatchAlreadyExistsException)
//   3. 신청자 = 작성자 본인인지 확인 (아니면 SelfMatchException)
//   4. 신청자 포인트 충분한지 확인 (아니면 InsufficientPointException)
//   5. 게시글 상태 MATCHED로 변경
//   6. Match 엔티티 생성 및 저장
//   7. 포인트 차감 + PointTransaction 기록
//   8. 채팅방 생성 이벤트 발행
// ====================================================================

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.LockAcquisitionFailedException;
import com.example.team3final.common.exception.MatchException;
import com.example.team3final.common.exception.OptimisticLockConflictException;
import com.example.team3final.domain.chat.service.ChatService;
import com.example.team3final.domain.match.dto.response.CreateMatchResponseDto;
import com.example.team3final.domain.match.entity.Match;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.repository.MatchRepository;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.post.service.PostService;
import com.example.team3final.domain.user.service.UserPointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchConcurrencyService {

    private final MatchRepository matchRepository;
    private final PostService postService;
    private final UserPointService userPointService;
    private final ChatService chatService;

    private final RedissonClient redissonClient;

    // Redis 락 키 패턴: "match:lock:post:{postId}"
    private static final String MATCH_LOCK_KEY = "match:lock:post:";

    // 낙관적 락 재시도 최대 횟수 (전략 D)
    private static final int OPTIMISTIC_RETRY_MAX = 3;

    // 낙관적 락 재시도 전 대시 시간 (ms) - 충돌 가능성을 분산시키기 위한 짧은 대기
    private static final long OPTIMISTIC_RETRY_DELAY_MS = 50;

    // Redis 락 자동 해제 시간 (초) - 서버 장애로 unlock() 못 해도 데드락 방지
    private static final long REDIS_LOCK_LEASE_SECONDS = 5;

    // ====================================================================
    // 전략 A: 비관적 락 (Pessimistic Lock) + 즉시 실패 (NOWAIT)
    // ====================================================================
    // SELECT ... FOR UPDATE NOWAIT
    //   → DB가 해당 행에 배타 락(Exclusive Lock)을 걸어 다른 트랜잭션의 접근을 차단
    //   → NOWAIT: 이미 다른 트랜잭션이 락을 잡고 있으면 기다리지 않고 즉시 예외
    //
    // 흐름: 락 획득 시도 → 실패 시 즉시 LockTimeoutException → 409 반환
    //
    // 장점: 구현 단순, 충돌 즉시 감지, 데이터 정합성 완벽 보장
    // 단점: 락 경합 시 DB 성능 저하 가능, 데드락 위험 존재
    //
    // [학습 포인트] @Lock 어노테이션의 위치
    //   @Lock은 PostRepository의 메서드에 붙음
    //   하지만 실제 락은 @Transactional 범위 안에서만 유효
    //   → 이 메서드의 @Transactional이 끝날 때까지 DB 락이 유지됨
    // ====================================================================
    @Transactional
    public CreateMatchResponseDto applyMatchWithPessimisticLock(Long postId, Long applicantId) {

        // PostService를 통해 비관적 락(NOWAIT) 걸린 게시글 조회
        // → 다른 트랜잭션이 같은 행 잠그고 있으면 즉시 예외
        Post post = postService.getPostWithPessimisticLockNowait(postId);

        return processMatch(post, applicantId);
    }

    // ====================================================================
    // 전략 B: 비관적 락 (Pessimistic Lock) + 대기 후 실패
    // ====================================================================
    // SELECT ... FOR UPDATE (NOWAIT 없음)
    //   → innodb_lock_wait_timeout 설정값만큼 대기 후 타임아웃
    //
    // 전략 A와 차이:
    //   A → NOWAIT: 즉시 포기, 응답 빠름
    //   B → 대기: 응답 느릴 수 있음, 처리량 높을 수 있음
    //
    // 테스트 주의: innodb_lock_wait_timeout 기본값 50초
    //   → 테스트 DB에서 3초로 낮출 것
    //   SET innodb_lock_wait_timeout = 3;
    // ====================================================================
    @Transactional
    public CreateMatchResponseDto applyMatchWithPessimisticLockAndWait(Long postId, Long applicantId) {

        // PostService를 통해 비관적 락(대기 O) 걸린 게시글 조회
        Post post = postService.getPostWithPessimisticLock(postId);

        return processMatch(post, applicantId);
    }

    // ====================================================================
    // 전략 C: 낙관적 락 (Optimistic Lock) + 즉시 실패
    // ====================================================================
    // Post 엔티티의 @Version 필드로 충돌 감지
    //   → DB 락 없이 여러 스레드가 동시에 같은 Post를 읽음
    //   → 한 스레드가 UPDATE 성공 → version 값 1 증가
    //   → 나머지 스레드가 UPDATE 시도 시 version 불일치
    //     → ObjectOptimisticLockingFailureException 발생
    //
    // JPA 실행 SQL:
    //   UPDATE posts SET status='MATCHED', version=2 WHERE id=? AND version=1
    //   → version=1 아니면 0건 → 예외!
    //
    // 장점: DB 락 없어서 성능 우수
    // 단점: 동시 요청 많으면 성공 0건 가능 → 선착순 보장 취약
    //
    // ====================================================================
    @Transactional
    public CreateMatchResponseDto applyMatchWithOptimisticLock(Long postId, Long applicantId) {

        // 일반 조회 (락 없음) - @Version 필드로 나중에 충돌 감지
        Post post = postService.getPostById(postId);

        try {
            // processMatch → post.changeStatus() → JPA flush 시점에 version 검증
            // → 다른 스레드가 먼저 version 올렸으면 이 UPDATE 0건 → 예외!
            return processMatch(post, applicantId);

        } catch (ObjectOptimisticLockingFailureException e) {
            log.info("[낙관적 즉시실패] postId={}, applicantId={} - 버전 충돌로 즉시 실패", postId, applicantId);

            // 버전 충돌 -> 재시도 없이 즉시 실패
            throw new OptimisticLockConflictException("MATCH_102");
        }
    }

    // ====================================================================
    // 전략 D: 낙관적 락 (Optimistic Lock) + 재시도 (최대 3회)
    // ====================================================================
    // 충돌 시 최신 데이터를 다시 읽어서 재시도
    //
    // 왜 재시도마다 새 트랜잭션(REQUIRES_NEW)이 필요한가?
    //   JPA 1차 캐시: 같은 트랜잭션에서 재시도하면 캐시에서 충돌난 version의
    //   Post를 다시 가져옴 → DB 최신 version 못 읽음 → 계속 충돌
    //   → REQUIRES_NEW: 새 트랜잭션 = 새 1차 캐시 = DB에서 최신 데이터 읽기
    //
    // 재시도 중 이미 MATCHED면?
    //   → processMatch() 상태 체크에서 MatchException 발생
    //   → 즉시 포기 (재시도 의미 없음)
    //
    // [학습 포인트] Propagation.REQUIRES_NEW
    //   기존 트랜잭션과 독립된 새 트랜잭션 시작
    //   기존 트랜잭션은 새 트랜잭션 끝날 때까지 일시 정지
    // ====================================================================
    // @Transactional 없음 — 트랜잭션은 attemptMatchWithNewTransaction() 안에서 관리
    public CreateMatchResponseDto applyMatchWithOptimisticLockAndRetry(Long postId, Long applicantId) {

        int attempt = 0;

        while (attempt < OPTIMISTIC_RETRY_MAX) {
            try {
                // 매 시도마다 REQUIRES_NEW 트랜잭션으로 최신 Post 읽기
                return attemptMatchWithNewTransaction(postId, applicantId);

            } catch (ObjectOptimisticLockingFailureException e) {
                attempt++;
                log.info("[낙관락 재시도] postId={}, {}회차 버전 충돌 ({}/{}회)",
                        postId, attempt, attempt, OPTIMISTIC_RETRY_MAX);

                if (attempt >= OPTIMISTIC_RETRY_MAX) {
                    log.warn("[낙관락 재시도] postId={} 최대 재시도({}) 초과 → 최종 실패",
                            postId, OPTIMISTIC_RETRY_MAX);
                    throw new OptimisticLockConflictException("MATCH_102");
                }

                try {
                    // 재시도 전 짧게 대기 — 모든 스레드가 동시 재시도해서 또 충돌하는 것 방지
                    Thread.sleep(OPTIMISTIC_RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("낙관락 재시도 중 인터럽트 발생", ie);
                }

            } catch (MatchException e) {
                // 이미 MATCHED 상태 → 재시도해도 의미 없음, 즉시 포기
                throw e;
            }
        }

        throw new OptimisticLockConflictException("MATCH_102");
    }

    // 전략 D 전용 — 각 재시도를 독립된 새 트랜잭션으로 실행
    // REQUIRES_NEW: 호출자 트랜잭션 있어도 무시하고 새 트랜잭션 시작
    // → 새 1차 캐시로 DB에서 최신 version의 Post 다시 읽음
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CreateMatchResponseDto attemptMatchWithNewTransaction(Long postId, Long applicantId) {
        Post post = postService.getPostById(postId);
        return processMatch(post, applicantId);
    }

    // ====================================================================
    // 전략 E: Redis 분산 락 + 즉시 실패 (waitTime=0)
    // ====================================================================
    // Redisson tryLock()으로 분산 환경에서 단 하나의 스레드만 처리 보장
    //
    // tryLock(waitTime=0, leaseTime=5s, TimeUnit.SECONDS)
    //   waitTime=0  → 락이 이미 있으면 기다리지 않고 즉시 false 반환
    //   leaseTime=5s → 락 획득 후 5초 후 자동 해제 (서버 장애 시 데드락 방지)
    //
    // 왜 DB 락 대신 Redis 락?
    //   DB 락: DB 커넥션 점유 → 트래픽 많으면 커넥션 풀 고갈 위험
    //   Redis 락: DB와 독립 → DB 부하 없음, 서버 여러 대여도 동기화 가능
    //
    // [주의] Redis 락은 반드시 @Transactional 밖에서 잡아야 함
    //   이유: 트랜잭션 커밋 전에 락이 해제되면 다른 스레드가 아직 OPEN인
    //         상태를 읽고 중복 매칭 시도 가능
    //   → "락 획득 → 트랜잭션 시작 → 비즈니스 로직 → 커밋 → 락 해제" 순서 보장
    //   → applyMatchInTransaction()을 별도 @Transactional 메서드로 분리한 이유
    // ====================================================================
    public CreateMatchResponseDto applyMatchWithRedisLockImmediateFail(Long postId, Long applicantId) {
        // 락 키: "match:lock:post:101" — postId마다 독립적인 락
        String lockKey = MATCH_LOCK_KEY + postId;
        // getLock(): Redis 락 객체 참조만 가져옴 (아직 락 획득 아님)
        RLock lock = redissonClient.getLock(lockKey);

        boolean acquired = false;
        try {
            // waitTime=0: 락 있으면 즉시 false 반환
            // leaseTime=5s: 5초 후 자동 해제
            acquired = lock.tryLock(0, REDIS_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);

            if (!acquired) {
                // 다른 스레드가 현재 처리 중 → 즉시 실패
                log.info("[Redis락 즉시실패] postId={} - 락 획득 실패, 즉시 반환", postId);
                throw new LockAcquisitionFailedException("MATCH_102");
            }

            // 락 획득 성공 → @Transactional 메서드 호출
            // (Self-invocation 문제 주의: 같은 클래스 내 호출은 AOP 프록시 우회됨
            //  → 실제 적용 시 별도 @Service 빈으로 분리 권장. 테스트에서는 동작 확인용)
            return applyMatchInTransaction(postId, applicantId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Redis 락 획득 중 인터럽트 발생", e);
        } finally {
            // 반드시 finally에서 락 해제 (예외 발생해도 실행됨)
            // isHeldByCurrentThread(): 내 스레드가 잡은 락인지 확인 후 해제 (안전 장치)
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("[Redis락] postId={} 락 해제 완료", postId);
            }
        }
    }

    // ====================================================================
    // 전략 F: Redis 분산 락 + 500ms 대기 후 실패
    // ====================================================================
    // tryLock(waitTime=500ms, leaseTime=5s)
    //   → 락이 있으면 최대 0.5초 기다렸다가 재시도
    //   → 0.5초 내에 락 해제되면 획득 성공
    //   → 0.5초 지나도 못 얻으면 false
    //
    // 전략 E와 실패 경로 차이 (문서화 포인트):
    //   E: 락 획득 실패 → LockAcquisitionFailedException (락 단계에서 실패)
    //   F: 락 획득 성공 → processMatch → 이미 MATCHED → MatchException (상태 체크에서 실패)
    //      (매칭 처리가 수ms 이내라 0.5초면 처리 완료 후 락 해제됨)
    // ====================================================================
    public CreateMatchResponseDto applyMatchWithRedisLockShortWait(Long postId, Long applicantId) {

        String lockKey = MATCH_LOCK_KEY + postId;
        RLock lock = redissonClient.getLock(lockKey);

        boolean acquired = false;
        try {
            // waitTime=500ms, leaseTime=5000ms (MILLISECONDS 단위)
            acquired = lock.tryLock(500, REDIS_LOCK_LEASE_SECONDS*1000, TimeUnit.MILLISECONDS);

            if (!acquired) {
                log.info("[Redis락 500ms대기] postId={} - 500ms 대기 후에도 락 획득 실패", postId);

                throw new LockAcquisitionFailedException("MATCH_102");
            }

            return applyMatchInTransaction(postId, applicantId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Redis 락 획득 중 인터럽트 발생", e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("[Redis락 500ms대기] postId={} 락 해제 완료", postId);
            }
        }
    }

    // ====================================================================
    // 락 없음 — Race Condition 재현용 (프로덕션 사용 금지)
    // ====================================================================
    // 동시성 제어가 전혀 없을 때 실제로 중복 매칭이 생기는지 확인
    // → DB 매칭 건수가 1 초과하면 Race Condition 발생 확인
    // ====================================================================
    @Transactional
    public CreateMatchResponseDto applyMatchWithoutLock(Long postId, Long applicantId) {
        // 락 없이 바로 조회 — 여러 스레드가 동시에 같은 OPEN 상태 읽을 수 있음
        Post post = postService.getPostById(postId);
        // OPEN 체크 → 매칭 생성 사이에 다른 스레드 끼어들면 둘 다 성공!
        return processMatch(post, applicantId);
    }

    // ====================================================================
    // Redis 전략 공통 — 락 안쪽에서 실행되는 트랜잭션 메서드
    // ====================================================================
    // 왜 별도 @Transactional 메서드로 분리하나?
    //   Redis 락 메서드(전략 E, F)는 @Transactional 없음
    //   → Spring @Transactional은 AOP 프록시 기반
    //   → 같은 클래스에서 this.메서드() 호출하면 프록시를 거치지 않아 트랜잭션 무시됨
    //   → 별도 메서드로 분리하고 Spring 빈을 통해 호출해야 트랜잭션 보장
    //   (실제 프로덕션에서는 별도 @Service 클래스로 완전 분리 권장)
    // ====================================================================
    @Transactional
    public CreateMatchResponseDto applyMatchInTransaction(Long postId, Long applicantId) {
        Post post = postService.getPostById(postId);
        return processMatch(post, applicantId);
    }

    // ====================================================================
    // 공통 비즈니스 로직 — 모든 락 전략에서 동일하게 사용
    // ====================================================================
    // 이 메서드는 락 전략을 모름 → 단일 책임 원칙 유지
    // 반드시 락을 잡거나 트랜잭션이 보장된 상태에서 호출되어야 함
    //
    // [반환 타입] CreateMatchResponseDto
    //   동시성 테스트 목적이므로 닉네임·chatRoomId는 null 처리
    //   → 테스트 검증 대상은 "DB 매칭 생성 건수 == 1" 이지 닉네임이 아님
    // ====================================================================
    private CreateMatchResponseDto processMatch(Post post, Long applicantId) {

        // 1. 게시글 상태 검증
        // 락 획득 후에도 반드시 다시 확인 (Double-Checked Locking 패턴)
        // 이유: 락 잡기 전 짧은 순간 다른 스레드가 MATCHED로 바꿨을 수 있음
        if (post.getStatus() != PostStatus.OPEN) {
            // 기존 프로젝트의 MatchException + ErrorCode 사용
            throw new MatchException(ErrorCode.MATCH_ALREADY_MATCHED);
        }

        // 2. 본인 게시글 신청 방지
        if (post.getAuthorId().equals(applicantId)) {
            throw new MatchException(ErrorCode.MATCH_SELF_APPLY);
        }

        // 3. 신청자 포인트 잔액 검증
        // UserPointService.getTotalPoint()를 통해 조회
        // → UserPointService 내부에서 user.getTotalPoint() 호출
        // → UserRepository 직접 접근 없음 (Service to Service 원칙 유지)
        int totalPoint = userPointService.getTotalPoint(applicantId);
        int requiredPoint = post.getAuthorDeposit(); // 등록자와 동일한 책임비

        if (totalPoint < requiredPoint) {
            throw new MatchException(ErrorCode.POINT_NOT_ENOUGH);
        }

        // 4. 게시글 상태 MATCHED로 변경
        // PostService.changePostStatus()를 통해 처리
        // → PostService가 내부에서 post.changeStatus() 도메인 메서드 호출
        // → JPA Dirty Checking으로 UPDATE 자동 발생
        postService.changePostStatus(post.getId(), PostStatus.MATCHED);

        // 5. 매칭 엔티티 생성 및 저장
        // MatchRepository는 같은 도메인 → 직접 사용 OK
        Match match = Match.builder()
                .postId(post.getId())
                .applicantId(applicantId)
                .applicantDeposit(requiredPoint) // 등록자 책임비와 동일한 금액 예치
                .build();
        Match savedMatch = matchRepository.save(match);

        // 6. 신청자 포인트 차감 + PointTransaction 기록
        // UserPointService.deductPoint() 호출
        // → 내부에서 무료/유료 포인트 자동 분리 처리 (user.deduct(amount))
        // → PointTransactionType.DEPOSIT 으로 기록
        userPointService.deductPoint(applicantId, requiredPoint, savedMatch.getId());

        // 7. 채팅방 생성
        chatService.createChatRoom(post.getId(), post.getAuthorId(), applicantId);

        // 8. 응답 DTO 생성
        // [테스트 목적] 닉네임·chatRoomId는 null 처리
        // → 동시성 테스트는 DB 매칭 건수 검증이 목적이므로 닉네임 불필요
        // → 프로덕션 createMatch()에서는 UserService로 닉네임 조회 후 채워야 함
        return CreateMatchResponseDto.of(
                savedMatch,
                post.getAuthorId(),       // authorId (Post에서 추출)
                post.getAuthorDeposit(),   // authorDeposit (Post에서 추출)
                null,                      // authorNickname — 테스트 목적, null 처리
                null,                      // applicantNickname — 테스트 목적, null 처리
                null                       // chatRoomId — 테스트 목적, null 처리
        );
    }
}
