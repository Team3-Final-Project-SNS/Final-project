package com.example.team3final.domain.user;

import com.example.team3final.common.exception.UserException;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.enums.Gender;
import com.example.team3final.domain.user.repository.UserRepository;
import com.example.team3final.domain.user.service.UserPointService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("포인트 동시 차감 정합성 테스트")
class PointConcurrencyTest {

    @Autowired
    private UserPointService userPointService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    // 테스트용 사용자 ID (매 테스트마다 새로 생성)
    private Long testUserId;

    // 동시 요청 스레드 수
    private static final int THREAD_COUNT = 10;

    // 초기 포인트 잔액
    private static final int INITIAL_POINT = 1000;

    // 1회 차감 금액
    private static final int DEDUCT_AMOUNT = 100;

    @BeforeEach
    void setUp() {
        // 테스트용 사용자 생성 (freePoint=1000)
        User user = User.builder()
                .email("point_test_" + System.nanoTime() + "@test.ac.kr")
                .password("testPassword123!")
                .name("포인트테스트유저")
                .nickname("포인트테스터" + System.nanoTime())
                .universityId(1L)
                .major("컴퓨터공학과")
                .studentNumber("20240001")
                .birthDate(LocalDate.of(2000, 1, 1))
                .gender(Gender.MALE)
                .build();

        User saved = userRepository.save(user);
        // 포인트는 빌더에 없으므로 도메인 메서드로 추가
        saved.addFreePoint(INITIAL_POINT);
        userRepository.save(saved);

        testUserId = saved.getId();
    }

    @AfterEach
    void tearDown() {
        TransactionStatus status = transactionManager.getTransaction(
                new DefaultTransactionDefinition()
        );
        try {
            userRepository.findById(testUserId)
                    .ifPresent(userRepository::delete);
            transactionManager.commit(status);
        } catch (Exception e) {
            transactionManager.rollback(status);
        }
    }

    // ====================================================================
    // 테스트 1: 핵심 — 동시 차감 시 잔액 정합성 보장
    // ====================================================================
    //
    // 시나리오: 잔액 1000P, 100P씩 10번 동시 차감
    //
    // 비관락이 제대로 동작한다면:
    //   모든 차감이 순차적으로 직렬화 → 10번 모두 성공 → 잔액 0P
    //
    // 비관락이 없다면:
    //   여러 스레드가 동시에 1000P를 읽고 → 각자 900P로 저장
    //   → 실제로는 100P만 차감된 것처럼 잔액이 틀어짐
    // ====================================================================

    @Test
    @Order(1)
    @DisplayName("동시 100P 차감 10회 -> 잔액이 정확히 0P여야 한다 (비관락 직렬화 검증)")
    void concurrentDeduct_shouldMaintainCorrectBalance() throws InterruptedException {
        // given
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        // when - 10개 스레드가 동시에 100P씩 차감
        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();

                    // 비관락이 적용된 deductPoint() 호출
                    // 내부에서 getUserOrThrowWithLock() → SELECT FOR UPDATE
                    userPointService.deductPoint(testUserId, DEDUCT_AMOUNT, null);

                    successCount.incrementAndGet();

                } catch (UserException e) {
                    // 잔액 부족 예외 (POINT_NOT_ENOUGH) → 정상 실패
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

        startLatch.countDown(); // 동시 출발
        doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        // then
        User user = userRepository.findById(testUserId).orElseThrow();
        int finalBalance = user.getTotalPoint();

        System.out.println("=".repeat(50));
        System.out.println("[포인트 동시 차감] 성공: " + successCount.get() + "회");
        System.out.println("[포인트 동시 차감] 실패: " + failCount.get() + "회");
        System.out.println("[포인트 동시 차감] 최종 잔액: " + finalBalance + "P");
        System.out.println("[포인트 동시 차감] 기대 잔액: 0P (1000P - 100P×10)");
        System.out.println("=".repeat(50));

        // 핵심 검증: 성공 횟수 × 차감금액 + 최종잔액 = 초기잔액
        // → 이 등식이 성립해야 포인트 정합성이 보장됨
        assertThat(successCount.get() * DEDUCT_AMOUNT + finalBalance)
                .as("성공 횟수 × 차감금액 + 최종잔액 = 초기잔액 (정합성 검증)")
                .isEqualTo(INITIAL_POINT);

        // 10번 모두 성공하고 잔액이 0이어야 함
        assertThat(successCount.get())
                .as("1000P / 100P = 10번 모두 성공해야 함")
                .isEqualTo(10);

        assertThat(finalBalance)
                .as("최종 잔액은 0P여야 한다")
                .isEqualTo(0);

    }

    // ====================================================================
    // 테스트 2: 잔액 초과 동시 차감 → 정확한 횟수만 성공
    // ====================================================================
    //
    // 시나리오: 잔액 1000P, 600P씩 5번 동시 차감 시도
    //   잔액으로는 1번(600P)만 성공 가능, 나머지 4번은 잔액 부족
    //
    // 비관락이 없다면:
    //   여러 스레드가 동시에 1000P를 읽고 → 모두 성공 → 잔액이 음수로 내려감
    //
    // 비관락이 있다면:
    //   1번째 차감 → 잔액 400P
    //   2번째부터 → 잔액 부족 예외 → 실패
    //   최종 잔액: 400P
    // ====================================================================

    @Test
    @Order(2)
    @DisplayName("잔액 초과 동시 차감 → 정확히 1번만 성공, 잔액이 음수가 되지 않아야 한다")
    void concurrentDeduct_overBalance_shouldNotGoBelowZero() throws InterruptedException {
        // 600P씩 5번 동시 차감 (잔액 1000P이므로 1번만 성공 가능)
        int deductAmount = 600;
        int threadCount = 5;

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    userPointService.deductPoint(testUserId, deductAmount, null);
                    successCount.incrementAndGet();

                } catch (UserException e) {
                    // 잔액 부족 → 정상 실패
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

        // then
        User user = userRepository.findById(testUserId).orElseThrow();
        int finalBalance = user.getTotalPoint();

        System.out.println("=".repeat(50));
        System.out.println("[잔액 초과 차감] 성공: " + successCount.get() + "회");
        System.out.println("[잔액 초과 차감] 실패: " + failCount.get() + "회");
        System.out.println("[잔액 초과 차감] 최종 잔액: " + finalBalance + "P");
        System.out.println("=".repeat(50));

        // 잔액이 절대 음수가 되면 안 됨 (핵심 정합성)
        assertThat(finalBalance)
                .as("잔액은 절대 음수가 되면 안 된다")
                .isGreaterThanOrEqualTo(0);

        // 정합성 검증
        assertThat(successCount.get() * deductAmount + finalBalance)
                .as("성공 횟수 × 차감금액 + 최종잔액 = 초기잔액")
                .isEqualTo(INITIAL_POINT);
    }

    // ====================================================================
    // 테스트 3: 단순 검증 — 비관락 단건 차감 정상 동작
    // ====================================================================

    @Test
    @Order(3)
    @DisplayName("단건 차감 → 정확한 금액만 차감되어야 한다")
    void singleDeduct_shouldDeductCorrectly() {
        // when
        userPointService.deductPoint(testUserId, 300, null);

        // then
        User user = userRepository.findById(testUserId).orElseThrow();
        assertThat(user.getTotalPoint())
                .as("1000P - 300P = 700P")
                .isEqualTo(700);
    }
}
