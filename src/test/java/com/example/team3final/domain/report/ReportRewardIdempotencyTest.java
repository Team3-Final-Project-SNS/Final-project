package com.example.team3final.domain.report;

import com.example.team3final.domain.report.entity.Report;
import com.example.team3final.domain.report.enums.ReportStatus;
import com.example.team3final.domain.report.enums.ReportReason;
import com.example.team3final.domain.report.repository.ReportRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("신고 포상금 중복 지급 방지 테스트")
class ReportRewardIdempotencyTest {

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private static final Long TEST_REPORTER_ID = 8001L;
    private static final Long TEST_TARGET_ID   = 8002L;
    private static final Long TEST_ADMIN_ID    = 8003L;

    // 각 테스트 후 테스트 데이터 정리
    @AfterEach
    void tearDown() {
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        try {
            reportRepository.deleteAll(
                    reportRepository.findAll().stream()
                            .filter(r -> r.getReporterId().equals(TEST_REPORTER_ID))
                            .toList()
            );
            transactionManager.commit(status);
        } catch (Exception e) {
            transactionManager.rollback(status);
        }
    }


    // ====================================================================
    // 테스트 1: 정상 케이스 — PENDING 신고 ACCEPTED 전환
    // ====================================================================
    // acceptIfPending() 이 PENDING → ACCEPTED 로 정상 전환하는지 확인
    // 반환값 1 = 변경된 행이 1건 = 정상 처리
    // ====================================================================

    @Test
    @Order(1)
    @Transactional
    @DisplayName("PENDING 신고를 처음 채택하면 affected rows = 1 이어야 한다")
    void acceptIfPending_firstTime_shouldReturnOne() {
        // given — PENDING 상태 신고 저장
        Report report = buildReport();
        Report saved = reportRepository.saveAndFlush(report);

        // when — 조건부 UPDATE 실행 (PENDING → ACCEPTED)
        int affected = reportRepository.acceptIfPending(saved.getId(), TEST_ADMIN_ID);

        // then — 변경된 행이 정확히 1건
        assertThat(affected)
                .as("PENDING → ACCEPTED 전환 시 affected rows 는 1이어야 한다")
                .isEqualTo(1);

        // DB에서 다시 조회해서 실제 상태가 바뀌었는지 확인
        Report updated = reportRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getStatus())
                .isEqualTo(ReportStatus.ACCEPTED);
    }

    // ====================================================================
    // 테스트 2: 핵심 케이스 — 이미 ACCEPTED 된 신고 재처리 차단
    // ====================================================================
    // 두 관리자가 동시에 채택 버튼을 누르는 시나리오 재현
    // 첫 번째 처리 → affected rows = 1 (성공)
    // 두 번째 처리 → affected rows = 0 (이미 처리됨 → 포상금 중복 지급 차단)
    // ====================================================================

    @Test
    @Order(2)
    @Transactional
    @DisplayName("이미 ACCEPTED 된 신고를 재처리하면 affected rows = 0 이어야 한다")
    void acceptIfPending_alreadyAccepted_shouldReturnZero() {
        // given — 첫 번째 관리자가 이미 채택 처리
        Report report = buildReport();
        Report saved = reportRepository.saveAndFlush(report);
        reportRepository.acceptIfPending(saved.getId(), TEST_ADMIN_ID);  // 1차 처리

        // when — 두 번째 관리자가 같은 신고를 다시 채택 시도
        int affected = reportRepository.acceptIfPending(saved.getId(), TEST_ADMIN_ID);

        // then — 이미 ACCEPTED 상태이므로 WHERE status='PENDING' 조건 불일치 → 0 반환
        assertThat(affected)
                .as("이미 ACCEPTED 된 신고는 affected rows 가 0 이어야 한다")
                .isEqualTo(0);
    }

    // ====================================================================
    // 테스트 3: REJECTED 신고도 재처리 차단
    // ====================================================================
    // 기각된 신고를 나중에 다시 채택하려 해도 막혀야 함
    // ====================================================================

    @Test
    @Order(3)
    @Transactional
    @DisplayName("이미 REJECTED 된 신고를 채택 시도하면 affected rows = 0 이어야 한다")
    void acceptIfPending_alreadyRejected_shouldReturnZero() {
        // given — 기각 처리된 신고
        Report report = buildReport();
        Report saved = reportRepository.saveAndFlush(report);
        reportRepository.rejectIfPending(saved.getId(), TEST_ADMIN_ID);  // 기각 처리

        // when — 같은 신고를 채택 시도
        int affected = reportRepository.acceptIfPending(saved.getId(), TEST_ADMIN_ID);

        // then
        assertThat(affected)
                .as("REJECTED 상태 신고는 채택 불가 → affected rows 0")
                .isEqualTo(0);
    }

    // ====================================================================
    // 테스트 4: 동시 채택 시도 — 두 관리자가 동시에 채택 버튼 클릭 재현
    // ====================================================================
    // 핵심: 동시에 10개 스레드가 같은 신고를 채택 시도
    // 기대: affected rows = 1인 스레드가 정확히 1개만 존재해야 함
    //       → 포상금 지급이 단 1회만 트리거되었음을 의미
    // ====================================================================

    @Test
    @Order(4)
    @DisplayName("동시에 여러 관리자가 같은 신고를 채택 시도하면 정확히 1번만 처리되어야 한다")
    void acceptIfPending_concurrent_onlyOneSucceeds() throws InterruptedException {

        // given — PENDING 신고를 먼저 저장 (별도 트랜잭션으로)
        Long savedId = saveReportInNewTransaction();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger skippedCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                // 각 스레드가 독립적인 트랜잭션을 직접 열고 닫음
                // PlatformTransactionManager 사용 — PointConcurrencyTest와 동일한 패턴
                TransactionStatus tx = transactionManager.getTransaction(
                        new DefaultTransactionDefinition()
                );
                try {
                    int affected = reportRepository.acceptIfPending(savedId, TEST_ADMIN_ID);
                    transactionManager.commit(tx);  // 커밋해야 DB에 반영되고 다른 스레드가 볼 수 있음

                    if (affected == 1) {
                        successCount.incrementAndGet();
                    } else {
                        skippedCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    transactionManager.rollback(tx);
                    skippedCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // then
        assertThat(successCount.get())
                .as("10개 동시 채택 시도 중 정확히 1번만 성공해야 한다 (포상금 중복 지급 방지)")
                .isEqualTo(1);
        assertThat(skippedCount.get())
                .as("나머지 9번은 affected rows = 0 으로 스킵되어야 한다")
                .isEqualTo(9);

        // DB 상태 최종 확인
        TransactionStatus tx = transactionManager.getTransaction(new DefaultTransactionDefinition());
        Report result = reportRepository.findById(savedId).orElseThrow();
        transactionManager.commit(tx);
        assertThat(result.getStatus()).isEqualTo(ReportStatus.ACCEPTED);
    }

    // ====================================================================
    // 헬퍼 메서드
    // ====================================================================

    private Report buildReport() {
        return Report.builder()
                .reporterId(TEST_REPORTER_ID)
                .targetId(TEST_TARGET_ID)
                .reason(ReportReason.SPAM)          // ReportReason enum 사용
                .detail("동시성 테스트용 신고 사유") // 선택값, null이어도 무방
                .build();
    }

    /**
     * 동시성 테스트용 — 별도 트랜잭션으로 신고 저장 후 ID 반환
     * 메인 테스트 메서드가 @Transactional 없으므로
     * saveAndFlush 자체도 트랜잭션이 필요해 직접 열어줌
     */
    private Long saveReportInNewTransaction() {
        TransactionStatus tx = transactionManager.getTransaction(new DefaultTransactionDefinition());
        try {
            Report saved = reportRepository.saveAndFlush(buildReport());
            transactionManager.commit(tx);
            return saved.getId();
        } catch (Exception e) {
            transactionManager.rollback(tx);
            throw e;
        }
    }
}
