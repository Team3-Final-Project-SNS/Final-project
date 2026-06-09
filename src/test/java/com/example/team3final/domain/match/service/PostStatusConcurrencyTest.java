package com.example.team3final.domain.match.service;

// ====================================================================
// PostStatusConcurrencyTest.java
//
// 목적: 포스트 상태 변경 중복 처리 방지 검증
//       실제 프로덕션 로직(MatchServiceImpl.createMatch())을 대상으로
//       1:1 매칭과 단체 매칭 두 케이스 모두 검증
//
// MatchConcurrencyTest와의 차이:
//   MatchConcurrencyTest  → MatchConcurrencyService (전략 비교 실험용) 대상
//   이 테스트              → MatchServiceImpl (실제 서비스) 대상
//
// 검증하는 불변조건:
//   [1:1]  Match 생성 수 = 정확히 1개, Post.status = MATCHED
//   [단체]  Match 생성 수 = 정확히 maxApplicants-1개 (신청자 수)
//           currentApplicants = maxApplicants
//           Post.status = MATCHED
//           currentApplicants > maxApplicants 절대 불가 (정원 초과 방지)
//
// ====================================================================

import com.example.team3final.common.exception.MatchException;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("[포스트 상태 변경] 중복 처리 방지 동시성 검증 테스트")
class PostStatusConcurrencyTest {

    // ===== 주입 =====

    // 실제 프로덕션 서비스 - Redis 락 + 전체 비즈니스 로직 포함
    @Autowired
    private MatchServiceImpl matchService;

    // Repository는 테스트 데이터 셋업·결과 검증 전용

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MatchRepository matchRepository;

    // JPA 1차 캐시를 강제로 비우는 데 사용
    // → entityManager.clear() 후 조회하면 DB에서 최신 상태를 직접 읽음
    @Autowired
    private EntityManager entityManager;

    // @AfterEach에서 @Transactional AOP가 동작 안 하는 경우를 대비해 수동 트랜잭션 관리
    @Autowired
    private PlatformTransactionManager transactionManager;

    // ===== 상수 =====

    // 동시에 신청할 가상 사용자 수 - 10명이면 Race Condition 재현에 충분
    private static final int THREAD_COUNT = 10;

    // 테스트용 책임비
    private static final int AUTHOR_DEPOSIT = 500;

    // ===== 인스턴스 변수 - 매 테스트마다 setUp()에서 초기화 =====

    private Long postId;
    private Long authorUserId;
    // 동시 신청자 ID 목록 - 매 테스트마다 새로 채움
    private List<Long> applicantUserIds = new ArrayList<>();

    // ===== setUp / tearDown =====

    @BeforeEach
    void setUp() {
        // 1. 게시글 등록자 생성
        User author = createTestUser(
                "author_" + System.nanoTime() + "@test.ac.kr",
                "등록자_" + System.nanoTime(),
                AUTHOR_DEPOSIT * 2 // 책임비보다 충분한 포인트
        );
        authorUserId = author.getId();

        // 2. THREAD_COUNT명의 신청자 생성 (각자 충분한 포인트 보유)
        for (int i = 0; i < THREAD_COUNT; i++) {
            User applicant = createTestUser(
                    "applicant_" + i + "_" + System.nanoTime() + "@test.ac.kr",
                    "신청자_" + i + "_" + System.nanoTime(),
                    AUTHOR_DEPOSIT * 2
            );
            applicantUserIds.add(applicant.getId());
        }
        // postId는 각 테스트에서 maxApplicants를 다르게 설정해 직접 생성
    }

    @AfterEach
    void tearDown() {
        // @AfterEach에서 @Transactional이 적용 안 되는 경우가 있어서
        // PlatformTransactionManager로 직접 트랜잭션을 열고 정리
        TransactionStatus status = transactionManager.getTransaction(
                new DefaultTransactionDefinition()
        );
        try {
            // 외래키 순서 고려: matches → posts → users
            matchRepository.deleteAllInBatch();
            // @SQLDelete(soft-delete)가 걸린 Post는 deleteAllInBatch()로 물리 삭제
            entityManager.createNativeQuery("DELETE FROM posts").executeUpdate();
            userRepository.deleteAllInBatch();
            transactionManager.commit(status);
        } catch (Exception e) {
            transactionManager.rollback(status);
        }
        // 다음 테스트를 위해 신청자 목록 초기화
        applicantUserIds.clear();
    }


    // ====================================================================
    // 테스트 1: 1:1 매칭 — 정확히 1명만 성공해야 한다
    // ====================================================================
    // [핵심 개념: Redis 분산 락 + waitTime=0 (즉시 실패)]
    //
    // MatchServiceImpl.createMatch() 내부 동작:
    //   1. maxApplicants == 2 → waitTime=0 (즉시 실패 전략)
    //   2. Redis 락 tryLock(0ms) → 실패 시 즉시 MatchException
    //   3. 락 성공 시 → createMatchInTransaction() 실행
    //   4. isFull() == false 확인 → increaseCurrentApplicants()
    //   5. isFull() == true → post.match() → status = MATCHED
    //
    // 불변조건:
    //   - Match 생성 수 = 정확히 1개
    //   - Post.status = MATCHED
    //   - Post.currentApplicants = 2 (등록자1 + 신청자1)
    // ====================================================================
    @Test
    @Order(1)
    @DisplayName("[1:1 매칭] 10명 동시 신청 → 정확히 1명만 성공, Post.status = MATCHED")
    void oneToOne_동시신청_1명만성공() throws InterruptedException {
        // given — 1:1 게시글 생성 (maxApplicants=2: 등록자1 + 신청자1)
        postId = createTestPost(authorUserId, AUTHOR_DEPOSIT, 2).getId();

        // CountDownLatch 설명:
        //   startLatch(1): 출발 총구 — countDown() 한 번으로 모든 스레드 동시 출발
        //   doneLatch(THREAD_COUNT): 수거함 — 모든 스레드가 완료 신호를 보낼 때까지 대기
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);

        // AtomicInteger: 멀티스레드 환경에서 안전한 카운터
        //   일반 int는 여러 스레드가 동시에 ++ 하면 값이 깨짐 (Race Condition)
        //   AtomicInteger는 CAS(Compare-And-Swap) 연산으로 원자적 증가 보장
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // ExecutorService: 스레드 풀 — THREAD_COUNT개 스레드를 병렬 실행
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        // when — THREAD_COUNT명이 동시에 매칭 신청
        for (int i = 0; i < THREAD_COUNT; i++) {
            final Long applicantId = applicantUserIds.get(i);
            executor.submit(() -> {
                try {
                    // 모든 스레드가 startLatch.countDown() 신호를 기다림
                    // → 신호 오는 순간 전원 동시 출발 (최대한 동시성 재현)
                    startLatch.await();

                    // 실제 프로덕션 서비스 호출 — Redis 락 포함 전체 로직
                    matchService.createMatch(postId, applicantId);
                    successCount.incrementAndGet(); // 성공 카운트

                } catch (MatchException e) {
                    System.out.println("[실패-MatchException] " + e.getMessage());
                    failCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("[실패-Interrupted]");
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    System.out.println("[실패-Exception] " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    failCount.incrementAndGet();
                } finally {
                    // 성공·실패 여부와 관계없이 반드시 완료 신호 전송
                    // → doneLatch.await()가 이 신호를 모아서 메인 스레드 재개
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();              // 출발 신호 → 모든 스레드 동시 출발!
        doneLatch.await(15, TimeUnit.SECONDS); // 최대 15초 대기
        executor.shutdown();

        // JPA 1차 캐시 초기화 — 캐시에 남아있는 stale 데이터를 무시하고 DB에서 직접 읽기
        // 이 코드 없으면: 테스트 스레드의 영속성 컨텍스트에 캐시된 값을 읽어서 오검증 가능
        entityManager.clear();

        // then
        // 검증 1: 애플리케이션 레벨에서 정확히 1명만 성공
        assertThat(successCount.get())
                .as("[1:1] 정확히 1명만 매칭 성공해야 한다")
                .isEqualTo(1);

        // 검증 2: DB에 Match 레코드가 정확히 1개만 생성됐는지
        //   → 동시성 제어 실패 시 여러 개 생성될 수 있음
        long dbMatchCount = matchRepository.countByPostId(postId);
        assertThat(dbMatchCount)
                .as("[1:1] DB Match 레코드는 정확히 1개여야 한다")
                .isEqualTo(1);

        // 검증 3: Post.status가 MATCHED로 변경됐는지
        Post post = postRepository.findById(postId).orElseThrow();
        assertThat(post.getStatus())
                .as("[1:1] Post 상태는 MATCHED여야 한다")
                .isEqualTo(PostStatus.MATCHED);

        // 검증 4: currentApplicants = maxApplicants (정원이 꽉 찼는지)
        assertThat(post.getCurrentApplicants())
                .as("[1:1] currentApplicants == maxApplicants(2) 이어야 한다")
                .isEqualTo(2);

        printResult("1:1 매칭 동시성 검증", successCount.get(), failCount.get(),
                dbMatchCount, post.getStatus(), post.getCurrentApplicants(), post.getMaxApplicants());
    }


    // ====================================================================
    // 테스트 2: 단체 매칭 — 정확히 maxApplicants-1명(신청자)만 성공해야 한다
    // ====================================================================
    // [핵심 개념: Redis 분산 락 + waitTime=500ms (대기 후 실패)]
    //
    // MatchServiceImpl.createMatch() 내부 동작:
    //   1. maxApplicants > 2 → waitTime=500ms (대기 전략)
    //   2. Redis 락 tryLock(500ms) → 기다렸다가 획득
    //   3. 락 성공 시 → createMatchInTransaction() 실행
    //   4. isFull() 체크 → false면 increaseCurrentApplicants()
    //   5. 마지막 신청자가 isFull() == true → post.match() → status = MATCHED
    //
    // 테스트 시나리오: maxApplicants=4 (등록자1 + 신청자3)
    //   → 10명이 동시 신청, 3명만 성공해야 함
    //   → 4번째부터는 isFull() 감지 → MATCH_ALREADY_MATCHED
    //
    // 불변조건:
    //   - Match 생성 수 = 정확히 3개 (maxApplicants-1)
    //   - Post.currentApplicants = 4 (maxApplicants)
    //   - Post.status = MATCHED
    //   - currentApplicants > maxApplicants 절대 불가 (핵심!)
    // ====================================================================
    @Test
    @Order(2)
    @DisplayName("[단체 매칭] 10명 동시 신청 → 정확히 3명만 성공, 정원 초과 없음")
    void group_동시신청_정원만성공() throws InterruptedException {
        // given — 단체 게시글 생성 (maxApplicants=4: 등록자1 + 신청자3)
        final int MAX_APPLICANTS = 4;     // 총 정원 (등록자 포함)
        final int EXPECTED_MATCH = MAX_APPLICANTS - 1; // 신청자 수 = 3

        postId = createTestPost(authorUserId, AUTHOR_DEPOSIT, MAX_APPLICANTS).getId();

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
                    matchService.createMatch(postId, applicantId);
                    successCount.incrementAndGet();

                } catch (MatchException e) {
                    // 정상 실패 경로:
                    //   MATCH_ALREADY_MATCHED — isFull() 또는 락 획득 실패
                    //   MATCH_DUPLICATE_APPLY — 중복 신청 차단
                    failCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        // 단체 매칭은 500ms 대기 전략이므로 타임아웃을 넉넉히 설정
        doneLatch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        // JPA 1차 캐시 초기화 — DB 최신 상태 직접 읽기
        entityManager.clear();

        // then
        // 검증 1: 정확히 EXPECTED_MATCH(3)명만 성공
        assertThat(successCount.get())
                .as("[단체] 정확히 %d명만 매칭 성공해야 한다".formatted(EXPECTED_MATCH))
                .isEqualTo(EXPECTED_MATCH);

        // 검증 2: DB Match 레코드가 정확히 EXPECTED_MATCH(3)개
        long dbMatchCount = matchRepository.countByPostId(postId);
        assertThat(dbMatchCount)
                .as("[단체] DB Match 레코드는 정확히 %d개여야 한다".formatted(EXPECTED_MATCH))
                .isEqualTo(EXPECTED_MATCH);

        Post post = postRepository.findById(postId).orElseThrow();

        // 검증 3: currentApplicants == maxApplicants (정원이 정확히 다 찼는지)
        assertThat(post.getCurrentApplicants())
                .as("[단체] currentApplicants == maxApplicants(%d) 이어야 한다".formatted(MAX_APPLICANTS))
                .isEqualTo(MAX_APPLICANTS);

        // 검증 4 (핵심 — 정원 초과 방지): currentApplicants > maxApplicants 절대 불가
        assertThat(post.getCurrentApplicants())
                .as("[단체] currentApplicants가 maxApplicants를 초과하면 안 된다")
                .isLessThanOrEqualTo(MAX_APPLICANTS);

        // 검증 5: Post.status가 MATCHED로 변경됐는지
        assertThat(post.getStatus())
                .as("[단체] Post 상태는 MATCHED여야 한다")
                .isEqualTo(PostStatus.MATCHED);

        printResult("단체 매칭 동시성 검증", successCount.get(), failCount.get(),
                dbMatchCount, post.getStatus(), post.getCurrentApplicants(), post.getMaxApplicants());
    }


    // ====================================================================
    // 테스트 3: 이미 MATCHED된 게시글에 추가 신청 차단
    // ====================================================================
    // [핵심 개념: 상태 검증 — Double-Checked Locking]
    //
    // 시나리오:
    //   이미 누군가가 매칭을 완료해서 Post.status = MATCHED 인 상태에서
    //   추가 신청이 들어오면 즉시 거부해야 함
    //
    // 이 테스트가 필요한 이유:
    //   Redis 락 이후 createMatchInTransaction() 진입 시
    //   status 체크(MATCHED 감지) → 즉시 예외
    //   → 락 획득에 성공했어도 상태 체크에서 막히는 경로 검증
    // ====================================================================
    @Test
    @Order(3)
    @DisplayName("[상태 검증] 이미 MATCHED된 게시글 - 모든 신청이 차단되어야 한다")
    void alreadyMatched_모든신청차단() throws InterruptedException {
        // given — MATCHED 상태 게시글 준비
        // 1. 게시글 생성 후
        Post post = createTestPost(authorUserId, AUTHOR_DEPOSIT, 2);
        postId = post.getId();

        // 2. 첫 번째 신청자로 정상 매칭 완료 → status = MATCHED
        matchService.createMatch(postId, applicantUserIds.get(0));

        // 3. 나머지 9명이 동시에 신청 시도
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT - 1); // 9명
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT - 1);

        for (int i = 1; i < THREAD_COUNT; i++) { // 1번부터 (0번은 이미 매칭)
            final Long applicantId = applicantUserIds.get(i);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    matchService.createMatch(postId, applicantId);
                    successCount.incrementAndGet();

                } catch (MatchException e) {
                    // 기대하는 결과: 전부 MATCH_ALREADY_MATCHED 또는 MATCH_POST_CLOSED
                    failCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
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

        entityManager.clear();

        // then
        // 검증 1: 추가 성공은 0명이어야 함
        assertThat(successCount.get())
                .as("[MATCHED 차단] 이미 매칭된 게시글에 추가 성공은 0명이어야 한다")
                .isEqualTo(0);

        // 검증 2: DB Match 레코드는 여전히 1개 (처음 것만)
        long dbMatchCount = matchRepository.countByPostId(postId);
        assertThat(dbMatchCount)
                .as("[MATCHED 차단] DB Match 레코드는 1개여야 한다")
                .isEqualTo(1);

        // 검증 3: Post.status는 여전히 MATCHED
        Post refreshedPost = postRepository.findById(postId).orElseThrow();
        assertThat(refreshedPost.getStatus())
                .as("[MATCHED 차단] Post 상태는 MATCHED를 유지해야 한다")
                .isEqualTo(PostStatus.MATCHED);

        printResult("이미 MATCHED된 게시글 추가 신청 차단", successCount.get(), failCount.get(),
                dbMatchCount, refreshedPost.getStatus(),
                refreshedPost.getCurrentApplicants(), refreshedPost.getMaxApplicants());
    }


    // ====================================================================
    // 테스트 4: 단체 매칭 — 정원 초과 불가 경계값 검증
    // ====================================================================
    // [핵심 개념: 경계값 분석 (Boundary Value Analysis)]
    //
    // 시나리오: maxApplicants=3 (등록자1 + 신청자2), 10명 동시 신청
    //   → 딱 2명만 성공해야 하고
    //   → currentApplicants가 3을 넘으면 안 됨 (동시성 제어 실패)
    //
    // 이 테스트가 중요한 이유:
    //   단체 매칭에서 "마지막 자리 1개 남은 상황"이 가장 위험
    //   → 2명이 동시에 isFull()=false 를 읽고 동시에 increaseCurrentApplicants()
    //   → Redis 락이 없으면 currentApplicants가 maxApplicants를 초과 가능
    // ====================================================================
    @Test
    @Order(4)
    @DisplayName("[단체 경계값] maxApplicants=3 — currentApplicants 절대 3 초과 불가")
    void group_경계값_정원초과불가() throws InterruptedException {
        // given — 3인 게시글 (등록자1 + 신청자2)
        final int MAX_APPLICANTS = 3;
        final int EXPECTED_MATCH = MAX_APPLICANTS - 1; // 신청자 2명

        postId = createTestPost(authorUserId, AUTHOR_DEPOSIT, MAX_APPLICANTS).getId();

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
                    matchService.createMatch(postId, applicantId);
                    successCount.incrementAndGet();

                } catch (MatchException | InterruptedException e) {
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    failCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        entityManager.clear();

        Post post = postRepository.findById(postId).orElseThrow();
        long dbMatchCount = matchRepository.countByPostId(postId);

        // then
        // 검증 1: 성공 수 = EXPECTED_MATCH(2)
        assertThat(successCount.get())
                .as("[경계값] 정확히 %d명만 성공해야 한다".formatted(EXPECTED_MATCH))
                .isEqualTo(EXPECTED_MATCH);

        // 검증 2 (가장 중요): currentApplicants가 maxApplicants를 절대 초과하면 안 됨
        assertThat(post.getCurrentApplicants())
                .as("[경계값] currentApplicants(%d)가 maxApplicants(%d)를 초과하면 안 된다"
                        .formatted(post.getCurrentApplicants(), MAX_APPLICANTS))
                .isLessThanOrEqualTo(MAX_APPLICANTS);

        // 검증 3: DB Match 레코드도 EXPECTED_MATCH 이하
        assertThat(dbMatchCount)
                .as("[경계값] DB Match 레코드는 %d개 이하여야 한다".formatted(EXPECTED_MATCH))
                .isLessThanOrEqualTo(EXPECTED_MATCH);

        // 검증 4: Post.status = MATCHED
        assertThat(post.getStatus())
                .as("[경계값] Post 상태는 MATCHED여야 한다")
                .isEqualTo(PostStatus.MATCHED);

        printResult("단체 매칭 경계값 검증 (maxApplicants=3)", successCount.get(), failCount.get(),
                dbMatchCount, post.getStatus(), post.getCurrentApplicants(), post.getMaxApplicants());
    }


    // ====================================================================
    // 헬퍼 메서드
    // ====================================================================

    /**
     * 테스트용 유저 생성
     *
     * User 빌더에는 freePoint 파라미터가 없음
     * → 빌더로 생성 후 addFreePoint() 도메인 메서드로 포인트 세팅
     */
    private User createTestUser(String email, String nickname, int freePoint) {
        User user = User.builder()
                .email(email)
                .password("encodedPassword123!")
                .name("테스트유저")
                .nickname(nickname)
                .universityId(1L)
                .major("컴퓨터공학과")
                .studentNumber("20240001")
                .birthDate(LocalDate.of(2000, 1, 1))
                .gender(Gender.MALE)
                .build();
        User savedUser = userRepository.save(user);

        // addFreePoint(): 내부에서 this.freePoint += amount 처리
        // → @Transactional 범위 내 Dirty Checking으로 UPDATE 자동 발생
        savedUser.addFreePoint(freePoint);
        return userRepository.save(savedUser);
    }

    /**
     * 테스트용 게시글 생성
     *
     * @param authorId      등록자 ID
     * @param authorDeposit 책임비 (포인트)
     * @param maxApplicants 총 정원 (등록자 포함)
     *                      1:1 = 2, 단체3인 = 3, 단체4인 = 4 ...
     */
    private Post createTestPost(Long authorId, int authorDeposit, int maxApplicants) {
        Post post = Post.builder()
                .authorId(authorId)
                .meetAt(LocalDateTime.now().plusHours(3))  // 3시간 후 만남 (미래 시간 필수)
                .placeName("정문")
                .placeLat(new BigDecimal("37.5665000"))
                .placeLng(new BigDecimal("126.9780000"))
                .content("포스트 상태 변경 동시성 테스트용 게시글")
                .authorDeposit(authorDeposit)
                .maxApplicants(maxApplicants)
                // status → 빌더 내부에서 OPEN 고정
                // currentApplicants → 빌더 내부에서 1 고정 (등록자 포함)
                .build();
        return postRepository.save(post);
    }

    /**
     * 테스트 결과 표준화 출력 (문서화용)
     */
    private void printResult(
            String testName,
            int success,
            int fail,
            long dbMatchCount,
            PostStatus postStatus,
            int currentApplicants,
            int maxApplicants
    ) {
        System.out.println("\n" + "=".repeat(65));
        System.out.println("📊 " + testName);
        System.out.println("-".repeat(65));
        System.out.printf("  ✅ 성공:               %d명%n", success);
        System.out.printf("  ❌ 실패:               %d명%n", fail);
        System.out.printf("  📌 총 요청:             %d명%n", success + fail);
        System.out.printf("  🗃  DB Match 레코드:    %d건%n", dbMatchCount);
        System.out.printf("  📋 Post.status:        %s%n", postStatus);
        System.out.printf("  👥 currentApplicants:  %d / %d%n", currentApplicants, maxApplicants);
        System.out.printf("  🔒 정원 초과 여부:        %s%n",
                currentApplicants > maxApplicants ? "⚠️ 초과! (동시성 제어 실패)" : "없음 ✅");
        System.out.println("=".repeat(65) + "\n");
    }
}
