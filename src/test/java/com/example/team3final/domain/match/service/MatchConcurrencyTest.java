package com.example.team3final.domain.match.service;

// ====================================================================
// MatchConcurrencyTest.java
//
// TC-ID: MATCH-012 — 동시 매칭 신청 동시성 테스트
// 시나리오: 여러 사용자가 동시에 동일 게시글에 매칭 신청
// 기대 결과: 정확히 1명만 성공, 나머지는 전부 실패
//
// 테스트 환경:
//   - DB: 로컬 MySQL (application-test.yml 설정)
//   - Redis: Docker Redis 실행 중
//   - 동시 요청 수: 10명
//
// 비교 전략 목록:
//   전략 A: 비관적 락 + 즉시 실패 (NOWAIT)
//   전략 B: 비관적 락 + 대기 후 실패
//   전략 C: 낙관적 락 + 즉시 실패
//   전략 D: 낙관적 락 + 재시도 (최대 3회)
//   전략 E: Redis 분산 락 + 즉시 실패 (waitTime=0)
//   전략 F: Redis 분산 락 + 500ms 대기 후 실패
//   락 없음: Race Condition 재현 (문제 발생 확인용)
//
// 결론 작성 위치: 파일 하단 주석 "테스트 결론" 섹션
// ====================================================================

import com.example.team3final.common.exception.LockAcquisitionFailedException;
import com.example.team3final.common.exception.MatchException;
import com.example.team3final.common.exception.OptimisticLockConflictException;
import com.example.team3final.domain.match.repository.MatchRepository;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.post.repository.PostRepository;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.enums.Gender;
import com.example.team3final.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("[MATCH-012] 매칭 신청 동시성 제어 전략 비교 테스트")
class MatchConcurrencyTest {

    //===== 주입 =====

    @Autowired
    private MatchConcurrencyService matchConcurrencyService;

    // Service to Service 규칙은 프로덕션 코드의 아키텍처 원칙
    // 테스트 클래스는 서비스 레이어가 아닌 검증 도구이므로 적용 대상이 아님
    // → Repository 직접 주입은 테스트 데이터 셋업(setUp)·결과 검증(then)에 사용
    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    //===== 상수 =====

    // 동시에 신청할 가상 사용자 수
    // → 10명이면 Race Condition을 충분히 재현할 수 있음
    private static final int THREAD_COUNT = 10;

    // 테스트용 책임비 (포인트)
    private static final int AUTHOR_DEPOSIT = 500;

    //===== 테스트 인스턴스 변수 =====

    private Long postId;                                    // 매 테스트마다 새로 생성되는 게시글 ID
    private Long authorUserId;                              // 게시글 등록자 ID
    private List<Long> applicantUserIds = new ArrayList<>(); // 동시 신청자 ID 목록


    // ====================================================================
    // 셋업 / 정리
    // ====================================================================

    @BeforeEach
    void setUp() {
        // 1. 게시글 등록자 생성 (충분한 포인트 보유)
        User author = createTestUser("author@test.ac.kr", "등록자", AUTHOR_DEPOSIT * 2);
        authorUserId = author.getId();

        // 2. THREAD_COUNT명의 신청자 생성 (각자 포인트 충분)
        for (int i = 0; i < THREAD_COUNT; i++) {
            User applicant = createTestUser(
                    "applicant" + i + "@test.ac.kr",
                    "신청자" + i,
                    AUTHOR_DEPOSIT * 2  // 책임비보다 충분히 많은 포인트
            );
            applicantUserIds.add(applicant.getId());
        }

        // 3. OPEN 상태의 게시글 생성
        Post post = createTestPost(authorUserId, AUTHOR_DEPOSIT);
        postId = post.getId();
    }

    @AfterEach
    void tearDown() {
        // @AfterEach에서 @Transactional이 적용 안 되는 경우가 있어서
        // 트랜잭션을 수동으로 열어서 처리
        TransactionStatus status = transactionManager.getTransaction(
                new DefaultTransactionDefinition()
        );
        try {
            matchRepository.deleteAllInBatch();
            entityManager.createNativeQuery("DELETE FROM posts").executeUpdate();
            userRepository.deleteAllInBatch();
            transactionManager.commit(status);
        } catch (Exception e) {
            transactionManager.rollback(status);
        }
        applicantUserIds.clear();
    }


    // ====================================================================
    // 전략 A: 비관적 락 (Pessimistic Lock) + 즉시 실패 (NOWAIT)
    // ====================================================================
    // 핵심 개념: SELECT ... FOR UPDATE NOWAIT
    //   → DB가 행에 배타 락을 걸고, 이미 잠겨있으면 즉시 LockTimeoutException
    //   → 락 획득 실패 = 즉시 포기
    //
    // 예상 결과:
    //   성공 1명, 실패 9명
    //   실패 경로: LockTimeoutException 또는 MatchException(MATCH_ALREADY_MATCHED)
    // ====================================================================
    @Test
    @Order(1)
    @DisplayName("전략A [비관락+즉시실패] - 정확히 1명만 매칭 성공해야 한다")
    void testPessimisticLock_ImmediateFail() throws InterruptedException {

        // given
        // CountDownLatch: 모든 스레드를 동시에 출발시키기 위한 신호탄
        // -> startLatch.await()로 대기하다가 startLatch.countDown() 호출 시 전부 동시 출발
        CountDownLatch startLatch = new CountDownLatch(1);
        // doneLatch: 모든 스레드가 완료될 때까지 메인 스레드 대기
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);

        // AtomicInteger: 멀티스레드 환경에서 안전한 카운터
        // → 일반 int++는 원자적(atomic)이지 않아서 Race Condition 발생 → AtomicInteger 사용
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // ExecutorService: 스레드 풀 — THREAD_COUNT개 스레드를 동시에 실행
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        // when - THREAD_COUNT명이 동시에 매칭 신청
        for (int i = 0; i < THREAD_COUNT; i++) {
            final Long applicantId = applicantUserIds.get(i);
            executor.submit(() -> {
                try {
                    startLatch.await(); // 출발 신호 대기 (동시 출발 보장)
                    matchConcurrencyService.applyMatchWithPessimisticLock(postId, applicantId);
                    successCount.incrementAndGet(); // 성공 카운트
                } catch (InterruptedException e) {
                    // startLatch.await() 대기 중 인터럽트 발생 → 스레드 상태 복원
                    Thread.currentThread().interrupt();
                    failCount.incrementAndGet();
                } catch (RuntimeException e) {
                    // 락 타임아웃, 이미 매칭됨 등 모든 실패 -> 실패 카운트
                    failCount.incrementAndGet();
                }finally {
                    doneLatch.countDown(); // 이 스레드 완료 신호
                }
            });
        }

        startLatch.countDown();                       // 모든 스레드 동시 출발!
        doneLatch.await(10, TimeUnit.SECONDS); // 최대 10초 대기
        executor.shutdown();

        // then
        assertThat(successCount.get())
                .as("정확히 1명만 매칭 성공해야 한다")
                .isEqualTo(1);

        assertThat(failCount.get())
                .as("나머지 %d명은 전부 실패해야 한다.".formatted(THREAD_COUNT - 1))
                .isEqualTo(THREAD_COUNT -1);

        // DB에도 매칭이 정확히 1건만 생성됐는지 검증
        assertThat(matchRepository.countByPostId(postId))
                .as("DB 매칭 레코드는 정확히 1개여야 한다")
                .isEqualTo(1);

        // 게시글 상태가 MATCHED로 변경됐는지 검증
        assertThat(postRepository.findById(postId).orElseThrow().getStatus())
                .as("게시글 상태는 MATCHED여야 한다")
                .isEqualTo(PostStatus.MATCHED);

        printResult("전략 A: 비관적 락 + 즉시 실패 (NOWAIT)", successCount.get(), failCount.get());
    }


    // ====================================================================
    // 전략 B: 비관적 락 (Pessimistic Lock) + 대기 후 실패
    // ====================================================================
    // 핵심 개념: SELECT ... FOR UPDATE (NOWAIT 없음)
    //   → innodb_lock_wait_timeout 설정값만큼 대기
    //   → 기본 50초 → 테스트 환경에서는 짧게 설정 권장
    //
    // 전략 A와 차이:
    //   A: 즉시 포기 → 응답 빠름
    //   B: 일정 시간 대기 → 락이 빨리 해제되면 성공 가능
    //      → 하지만 매칭은 1명만 성공 가능이므로 결국 나머지는 실패
    //
    // 주의: doneLatch.await() 타임아웃을 길게 설정 (30초)
    // ====================================================================
    @Test
    @Order(2)
    @DisplayName("전략B [비관락+대기후실패] - 정확히 1명만 매칭 성공해야 한다.")
    void testPessimisticLock_WaitAndFail() throws InterruptedException {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            final Long applicantId = applicantUserIds.get(i);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    matchConcurrencyService.applyMatchWithPessimisticLockAndWait(postId, applicantId);
                    successCount.incrementAndGet();

                } catch (InterruptedException e) {
                    // startLatch.await() 대기 중 인터럽트 발생 → 스레드 상태 복원
                    Thread.currentThread().interrupt();
                    failCount.incrementAndGet();
                } catch (RuntimeException e) {
                    // 락 타임아웃, 이미 매칭됨(MatchException은 RuntimeException 하위) 등 모든 실패
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        // 전략 B는 대기 시간이 있어서 타임아웃을 길게 설정
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // then
        assertThat(successCount.get()).as("정확히 1명만 성공").isEqualTo(1);
        assertThat(failCount.get()).as("나머지 %d명 실패".formatted(THREAD_COUNT - 1))
                .isEqualTo(THREAD_COUNT - 1);
        assertThat(matchRepository.countByPostId(postId)).as("DB 매칭 1건").isEqualTo(1);
        assertThat(postRepository.findById(postId).orElseThrow().getStatus())
                .isEqualTo(PostStatus.MATCHED);

        printResult("전략 B: 비관적 락 + 대기 후 실패", successCount.get(), failCount.get());
    }


    // ====================================================================
    // 전략 C: 낙관적 락 (Optimistic Lock) + 즉시 실패
    // ====================================================================
    // 핵심 개념: Post 엔티티의 @Version 필드
    //   → 여러 스레드가 동시에 같은 version의 Post를 읽음
    //   → 한 스레드가 UPDATE 성공 → version 1 증가
    //   → 나머지 스레드가 UPDATE 시도 시 version 불일치
    //     → ObjectOptimisticLockingFailureException
    //
    // ⚠️ 주의: 경쟁이 매우 심하면 성공 0건 가능
    //   → 모든 스레드가 정확히 동시에 충돌하면 아무도 못 씀
    //   → 이 경우 assertThat(successCount).isLessThanOrEqualTo(1) 로 검증
    //   → 선착순 보장이 필요한 경우 이 전략은 부적합
    // ====================================================================
    @Test
    @Order(3)
    @DisplayName("전략C [낙관락+즉시실패] - 최대 1명만 매칭 성공해야 한다 (0명 가능)")
    void testOptimisticLock_ImmediateFail() throws InterruptedException {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            final Long applicantId = applicantUserIds.get(i);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    matchConcurrencyService.applyMatchWithOptimisticLock(postId, applicantId);
                    successCount.incrementAndGet();

                } catch (OptimisticLockConflictException | MatchException e) {
                    // 버전 충돌 또는 이미 매칭됨 → 실패
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        long dbMatchCount = matchRepository.countByPostId(postId);

        // ⚠️ 낙관적 락 즉시 실패의 한계:
        //    동시 요청이 많으면 successCount=0, dbMatchCount=0 가능
        //    → "1명 이하만 성공" 으로 검증
        assertThat(successCount.get())
                .as("낙관락 즉시실패: 최대 1명 성공 (0명 가능 — 전략의 한계)")
                .isLessThanOrEqualTo(1);

        assertThat(dbMatchCount)
                .as("DB 매칭 레코드는 0개 또는 1개 (전략의 한계)")
                .isLessThanOrEqualTo(1);

        System.out.println("[전략C 분석] 성공=" + successCount.get()
                + " / DB매칭=" + dbMatchCount
                + (successCount.get() == 0 ? " ⚠️ 아무도 성공 못함 (선착순 보장 실패)" : " ✔️ 정상"));

        printResult("전략 C: 낙관적 락 + 즉시 실패", successCount.get(), failCount.get());
    }


    // ====================================================================
    // 전략 D: 낙관적 락 (Optimistic Lock) + 재시도 (최대 3회)
    // ====================================================================
    // 핵심 개념: 충돌 시 최신 데이터를 다시 읽어서 재시도
    //   → 재시도 시 이미 MATCHED면 즉시 포기
    //   → 재시도마다 REQUIRES_NEW 트랜잭션으로 최신 Post 읽기
    //
    // 전략 C와 차이:
    //   C: 충돌 시 즉시 포기 → 성공 0건 가능
    //   D: 충돌 시 재시도 → 성공 1건 가능성 높음
    //
    // 예상 결과: 성공 1명, 실패 9명
    // ====================================================================
    @Test
    @Order(4)
    @DisplayName("전략D [낙관락+재시도] - 정확히 1명만 매칭 성공해야 한다")
    void testOptimisticLock_WithRetry() throws InterruptedException {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        AtomicInteger retryExhaustedCount = new AtomicInteger(0); // 재시도 소진 횟수

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            final Long applicantId = applicantUserIds.get(i);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    matchConcurrencyService.applyMatchWithOptimisticLockAndRetry(postId, applicantId);
                    successCount.incrementAndGet();

                } catch (OptimisticLockConflictException e) {
                    // 최대 재시도 횟수 초과 → 실패
                    retryExhaustedCount.incrementAndGet();
                    failCount.incrementAndGet();
                } catch (MatchException e) {
                    // 재시도 중 이미 MATCHED 확인 → 즉시 포기
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        // 재시도 로직 포함 → 타임아웃 15초로 설정
        doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        System.out.println("[전략D 분석] 재시도 소진 횟수: " + retryExhaustedCount.get());

        assertThat(successCount.get()).as("재시도 포함 낙관락: 정확히 1명 성공").isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(THREAD_COUNT - 1);
        assertThat(matchRepository.countByPostId(postId)).isEqualTo(1);
        assertThat(postRepository.findById(postId).orElseThrow().getStatus())
                .isEqualTo(PostStatus.MATCHED);

        printResult("전략 D: 낙관적 락 + 재시도 (3회)", successCount.get(), failCount.get());
    }


    // ====================================================================
    // 전략 E: Redis 분산 락 + 즉시 실패 (waitTime=0)
    // ====================================================================
    // 핵심 개념: Redisson tryLock(waitTime=0, leaseTime=5s)
    //   waitTime=0  → 락이 이미 있으면 즉시 false → LockAcquisitionFailedException
    //   leaseTime=5s → 서버 장애 시 5초 후 자동 해제 (데드락 방지)
    //
    // DB 락 vs Redis 락:
    //   DB 락: DB 커넥션 점유 → 커넥션 풀 고갈 위험
    //   Redis 락: DB 독립 → DB 부하 없음, 분산 서버 환경 지원
    //
    // 실패 경로: LockAcquisitionFailedException (락 단계에서 즉시 실패)
    // ====================================================================
    @Test
    @Order(5)
    @DisplayName("전략E [Redis락+즉시실패] - 정확히 1명만 매칭 성공해야 한다")
    void testRedisDistributedLock_ImmediateFail() throws InterruptedException {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        AtomicInteger lockFailCount = new AtomicInteger(0); // 락 획득 실패 횟수 (참고용)

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            final Long applicantId = applicantUserIds.get(i);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    matchConcurrencyService.applyMatchWithRedisLockImmediateFail(postId, applicantId);
                    successCount.incrementAndGet();

                } catch (LockAcquisitionFailedException e) {
                    // Redis 락 획득 실패 → 즉시 실패 (락 단계에서 차단됨)
                    lockFailCount.incrementAndGet();
                    failCount.incrementAndGet();
                } catch (MatchException e) {
                    // 락 획득 후 이미 MATCHED 확인 (극히 드문 케이스)
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        System.out.println("[전략E 분석] Redis 락 획득 실패 횟수: " + lockFailCount.get());

        assertThat(successCount.get()).as("정확히 1명만 성공").isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(THREAD_COUNT - 1);
        assertThat(matchRepository.countByPostId(postId)).isEqualTo(1);
        assertThat(postRepository.findById(postId).orElseThrow().getStatus())
                .isEqualTo(PostStatus.MATCHED);

        printResult("전략 E: Redis 분산 락 + 즉시 실패", successCount.get(), failCount.get());
    }


    // ====================================================================
    // 전략 F: Redis 분산 락 + 500ms 대기 후 실패
    // ====================================================================
    // 핵심 개념: Redisson tryLock(waitTime=500ms, leaseTime=5s)
    //   waitTime=500ms → 0.5초 기다렸다가 락이 없으면 false
    //   → 매칭 처리가 수ms 이내이므로 0.5초면 처리 완료 후 락 해제됨
    //
    // 전략 E와 실패 경로 차이 (핵심 비교 포인트):
    //   E: 락 획득 실패 → LockAcquisitionFailedException (락 단계)
    //   F: 락 획득 성공 → MatchException(MATCH_ALREADY_MATCHED) (상태 체크 단계)
    //   → 두 전략의 lockFailCount vs matchFailCount를 비교해서 문서화
    // ====================================================================
    @Test
    @Order(6)
    @DisplayName("전략F [Redis락+500ms대기] - 정확히 1명만 매칭 성공해야 한다")
    void testRedisDistributedLock_ShortWait() throws InterruptedException {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        AtomicInteger lockFailCount = new AtomicInteger(0);   // 락 단계 실패
        AtomicInteger matchFailCount = new AtomicInteger(0);  // 상태 체크 단계 실패

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            final Long applicantId = applicantUserIds.get(i);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    matchConcurrencyService.applyMatchWithRedisLockShortWait(postId, applicantId);
                    successCount.incrementAndGet();

                } catch (LockAcquisitionFailedException e) {
                    // 500ms 기다려도 락 획득 실패 (락 단계)
                    lockFailCount.incrementAndGet();
                    failCount.incrementAndGet();
                } catch (MatchException e) {
                    // 락 획득 후 이미 MATCHED 확인 (상태 체크 단계)
                    // → 전략 F에서 주로 발생하는 경로
                    matchFailCount.incrementAndGet();
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        // 전략 E vs F 실패 경로 비교 출력 (문서화 핵심)
        System.out.println("[전략F 분석] 락 단계 실패: " + lockFailCount.get()
                + " / 상태 체크 단계 실패: " + matchFailCount.get());

        assertThat(successCount.get()).as("정확히 1명만 성공").isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(THREAD_COUNT - 1);
        assertThat(matchRepository.countByPostId(postId)).isEqualTo(1);
        assertThat(postRepository.findById(postId).orElseThrow().getStatus())
                .isEqualTo(PostStatus.MATCHED);

        printResult("전략 F: Redis 분산 락 + 500ms 대기", successCount.get(), failCount.get());
    }


    // ====================================================================
    // 락 없음 — Race Condition 재현 (문제 발생 확인용)
    // ====================================================================
    // 이 테스트의 목적:
    //   "락이 없으면 실제로 문제가 생기는가?" 를 증명
    //   → DB 매칭 건수 > 1 이면 Race Condition 발생 확인
    //
    // ⚠️ 이 테스트는 실패해도 됨 (오히려 실패해야 의미 있음)
    //    환경에 따라 항상 재현되지 않을 수 있음 → 여러 번 실행 권장
    // ====================================================================
    @Test
    @Order(7)
    @DisplayName("락 없음 [Race Condition 재현] - DB 매칭 1건 초과 가능성 확인")
    void testNoLock_RaceConditionVerification() throws InterruptedException {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            final Long applicantId = applicantUserIds.get(i);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    matchConcurrencyService.applyMatchWithoutLock(postId, applicantId);
                    successCount.incrementAndGet();

                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        long dbMatchCount = matchRepository.countByPostId(postId);

        // 결과 출력 (이 테스트는 assert 실패가 "목적 달성"을 의미)
        System.out.println("========================================");
        System.out.println("[락 없음] 애플리케이션 성공 횟수: " + successCount.get());
        System.out.println("[락 없음] DB 실제 매칭 건수: " + dbMatchCount);
        System.out.println("[락 없음] Race Condition 발생 여부: "
                + (dbMatchCount > 1 ? "⚠️ 발생! (" + dbMatchCount + "건 생성됨)" : "미발생 (재실행 권장)"));
        System.out.println("========================================");

        // assert 하지 않음 — 현상 확인이 목적
        // dbMatchCount > 1 이면 동시성 문제가 실제로 존재함을 증명
    }


    // ====================================================================
    // 헬퍼 메서드
    // ====================================================================

    /**
     * 테스트용 유저 생성
     *
     * User 빌더에는 freePoint·paidPoint 파라미터가 없음
     * → 빌더로 생성 시 내부에서 freePoint=0, paidPoint=0 으로 고정
     * → 저장 후 addFreePoint()로 포인트 직접 세팅
     *   (addFreePoint는 도메인 메서드 → 엔티티의 상태 변경을 통해 Dirty Checking으로 UPDATE)
     */
    private User createTestUser(String email, String nickname, int freePoint) {
        // 1. User 엔티티 생성 (빌더 파라미터는 실제 User.java @Builder와 동일하게)
        User user = User.builder()
                .email(email)
                .password("encodedPassword123!") // 테스트용 임의 인코딩 값
                .name("테스트유저")
                .nickname(nickname)
                .universityId(1L)                // 테스트용 대학 ID
                .major("컴퓨터공학과")
                .studentNumber("20240001")
                .birthDate(LocalDate.of(2000, 1, 1))
                .gender(Gender.MALE)
                .build();
        // 2. 먼저 저장 (id 생성)
        User savedUser = userRepository.save(user);

        // 3. 포인트 세팅 — 빌더에 없으므로 도메인 메서드로 별도 추가
        //    addFreePoint()는 내부에서 this.freePoint += amount 처리
        //    @Transactional 범위 내에서 Dirty Checking으로 UPDATE 자동 발생
        savedUser.addFreePoint(freePoint);
        return userRepository.save(savedUser); // 포인트 반영 저장
    }

    /**
     * 테스트용 게시글 생성
     *
     * Post 빌더에는 status·currentApplicants 파라미터가 없음
     * → status: 빌더 내부에서 PostStatus.OPEN 으로 고정
     * → currentApplicants: 빌더 내부에서 1 로 고정 (등록자 1명)
     * → maxApplicants: 2 미만이면 내부에서 2로 보정됨 (등록자1 + 신청자1)
     *   동시성 테스트는 1:1 매칭이므로 maxApplicants=2 로 설정
     */
    private Post createTestPost(Long authorId, int authorDeposit) {
        Post post = Post.builder()
                .authorId(authorId)
                .meetAt(LocalDateTime.now().plusHours(3)) // 3시간 후 만남 (미래 시간 필수)
                .placeName("정문")
                .placeLat(new BigDecimal("37.5665000"))
                .placeLng(new BigDecimal("126.9780000"))
                .content("동시성 테스트용 게시글")
                .authorDeposit(authorDeposit)
                .maxApplicants(2) // 등록자(1) + 신청자(1) = 2명, 1:1 매칭 기준
                // status → 빌더 내부에서 OPEN 고정 (외부 주입 불가)
                // currentApplicants → 빌더 내부에서 1 고정 (등록자 포함)
                .build();
        return postRepository.save(post);
    }

    /**
     * 전략별 결과를 표준화된 포맷으로 출력 (문서화용)
     */
    private void printResult(String strategyName, int success, int fail) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("📊 " + strategyName);
        System.out.println("-".repeat(60));
        System.out.printf("  ✅ 성공: %d명%n", success);
        System.out.printf("  ❌ 실패: %d명%n", fail);
        System.out.printf("  📌 총 요청: %d명%n", success + fail);
        System.out.printf("  결과: %s%n",
                success == 1 ? "✔️ 정상 (1명만 성공)" : "⚠️ 비정상 (선착순 보장 실패)");
        System.out.println("=".repeat(60) + "\n");
    }
}

// ====================================================================
// 📋 테스트 결론
// ====================================================================
//
// 테스트 실행 일자: 2026.06.05
// 테스트 환경: MySQL 8.x (로컬), Redis 7.x (Docker), JVM 17, 동시 요청 10개
// 배포 환경: 멀티서버 (분산 환경)
//
// ※ 응답시간은 서버 기동/종료 포함 → 전략 간 상대적 비교 참고용
// ※ 락 없음은 테스트 환경 특성상 Race Condition 미재현 → 실운영에서는 발생 가능
//
// ┌────────────────────┬──────┬────────┬────────────┬──────────────┬──────────────────────────┐
// │ 전략                │ 성공  │DB매칭수 │ 선착순보장   │ 응답시간(ms)  │ 실패 경로                 │
// ├────────────────────┼──────┼────────┼────────────┼──────────────┼──────────────────────────┤
// │ A: 비관락 즉시실패    │  1명 │   1건  │     ✅     │    1,427     │ LockTimeoutException     │
// │ B: 비관락 대기실패    │  1명 │   1건  │     ✅     │    1,235     │ LockTimeoutException     │
// │ C: 낙관락 즉시실패    │  1명 │   1건  │   ⚠️ 불안정 │    1,738     │ OptimisticLockConflict   │
// │ D: 낙관락 재시도     │  1명 │   1건   │     ✅     │    1,481     │ 재시도 소진 후 실패        │
// │ E: Redis 즉시실패    │  1명 │   1건  │     ✅     │    1,443     │ LockAcquisitionFailed   │
// │ F: Redis 500ms대기  │  1명 │   1건   │     ✅     │    1,582     │ 락실패 or 상태체크실패     │
// │ 락 없음             │  1명 │   1건   │   ⚠️ 불안정 │    1,761     │ Race Condition 미재현    │
// └────────────────────┴──────┴────────┴────────────┴──────────────┴──────────────────────────┘
//
//
// ====================================================================
// ✅ 최종 채택 전략: 매칭 타입에 따라 분기
// ====================================================================
//
// ┌──────────────────────────────────────────────────────────────────┐
// │  1:1 매칭  → 전략 E: Redis 분산 락 + 즉시 실패 (waitTime=0)          │
// │  단체 매칭 → 전략 F: Redis 분산 락 + 500ms 대기                      │
// └──────────────────────────────────────────────────────────────────┘
//
// [1:1 매칭에서 전략 E를 선택한 이유]
//
//   1. 멀티서버 환경 대응
//      비관락·낙관락은 DB 기반 → 서버 증설 시 DB 커넥션 경합 선형 증가
//      Redis 락은 서버 몇 대든 Redis 하나로 중앙 동기화 → 수평 확장에 유리
//
//   2. DB 부하 최소화
//      비관락의 SELECT FOR UPDATE는 락 해제까지 DB 커넥션 점유
//      Redis 락은 DB와 독립 동작 → 트래픽 폭증 시 DB 커넥션 풀 보호
//
//   3. 선착순 특성상 즉시 실패(E)가 최적
//      1:1 매칭에서 남은 자리는 단 1개
//      락을 기다려서 획득해도 이미 MATCHED → 결국 실패, 500ms 지연만 발생
//      즉시 실패 = 빠른 409 응답 = 더 나은 UX
//
//   4. 데드락 안전
//      leaseTime=5s → 서버 장애 시에도 5초 후 자동 락 해제
//      비관락의 데드락 위험 없음
//
//
// [단체 매칭에서 전략 F를 선택한 이유]
//
//   단체 매칭 (예: 5명 모집, 현재 2자리 남은 상황) 에서는
//   E와 F의 결과가 달라짐
//
//   전략 E (즉시 실패) — 남은 자리 2명, 동시 10명 신청:
//     1번: 락 획득 → 성공 ✅ (1번째 자리 채움)
//     2~10번: 락 획득 실패 → 즉시 전부 실패 ❌
//     결과: 2번째 자리가 영구 공석 → 서비스 정책 위반
//
//   전략 F (500ms 대기) — 남은 자리 2명, 동시 10명 신청:
//     1번: 락 획득 → 성공 ✅ → 락 해제 (수ms)
//     2번: 기다렸다가 락 획득 → 성공 ✅ (2번째 자리 채움)
//     3~10번: 락 획득 → 이미 FULL → 실패 ❌
//     결과: 남은 자리 2개를 정확히 채움 ✅
//
//   → 매칭 처리는 수ms 이내이므로 500ms 대기는 충분한 여유
//   → 남은 자리 수만큼 순차적으로 락을 획득해 정확히 채울 수 있음
//
//
// [실제 구현 분기 예시]
//
//   public MatchResponse applyMatch(Long postId, Long applicantId) {
//       Post post = postService.getPostById(postId);
//
//       if (post.getMaxApplicants() == 2) {
//           // 1:1 매칭 (등록자1 + 신청자1) → 즉시 실패
//           return applyMatchWithRedisLockImmediateFail(postId, applicantId);
//       } else {
//           // 단체 매칭 (남은 자리 여러 개) → 500ms 대기
//           return applyMatchWithRedisLockShortWait(postId, applicantId);
//       }
//   }
//
//
// ⚠️ 기각된 전략과 이유:
//
//   - 전략 A, B (비관락):
//       멀티서버 환경에서 DB 커넥션 점유로 인한 커넥션 풀 고갈 위험
//       서버 증설 시 DB 락 경합 선형 증가 → 확장성 부적합
//
//   - 전략 C (낙관락 즉시실패):
//       동시 요청 폭증 시 모두 version 충돌 → 성공 0건 가능
//       선착순 보장 불안정 → 서비스 정책 위반 가능성
//       이번 테스트에서는 1명 성공했으나 환경에 따라 0건 가능
//
//   - 전략 D (낙관락 재시도):
//       재시도마다 DB 조회 발생 → 경쟁 심할수록 DB 부하 증가
//       재시도 간격(50ms) 동안 응답 지연
//       멀티서버에서도 DB 기반이라 확장성 한계
//
//   - 락 없음:
//       테스트 환경에서 우연히 Race Condition 미재현
//       실제 운영 트래픽에서 중복 매칭 발생 가능 → 절대 사용 불가
//
// ====================================================================