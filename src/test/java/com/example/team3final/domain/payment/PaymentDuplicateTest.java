package com.example.team3final.domain.payment;

import com.example.team3final.domain.payment.entity.Payment;
import com.example.team3final.domain.payment.enums.ChargePackage;
import com.example.team3final.domain.payment.enums.PaymentStatus;
import com.example.team3final.domain.payment.repository.PaymentRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("결제 중복 처리 방어 검증 테스트")
class PaymentDuplicateTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    // 테스트용 고정값
    private static final Long TEST_USER_ID = 9999L;
    private static final String TEST_MERCHANT_UID = "hankki_test_000001";

    @AfterEach
    void tearDown() {
        TransactionStatus status = transactionManager.getTransaction(
                new DefaultTransactionDefinition()
        );
        try {
            paymentRepository.deleteAll(
                    paymentRepository.findAll().stream()
                            .filter(p -> p.getMerchantUid().startsWith("hankki_test_"))
                            .toList()
            );
            transactionManager.commit(status);
        } catch (Exception e) {
            transactionManager.rollback(status);
        }
    }

    // ====================================================================
    // 테스트 1: 정상 케이스 — READY 상태 Payment 정상 생성
    // ====================================================================

    @Test
    @Order(1)
    @DisplayName("결제 준비(READY) Payment 정상 생성 → 저장 성공해야 한다")
    void createPayment_shouldSaveSuccessfully() {
        // given
        Payment payment = buildPayment(TEST_MERCHANT_UID);

        // when & then
        assertThatCode(() -> paymentRepository.saveAndFlush(payment))
                .doesNotThrowAnyException();

        // READY 상태로 저장됐는지 확인
        Payment saved = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(saved.getMerchantUid()).isEqualTo(TEST_MERCHANT_UID);
    }

    // ====================================================================
    // 테스트 2: 1차 방어 — 이미 PAID된 결제에 verifyPayment 중복 호출
    // ====================================================================
    //
    // 방어 위치: PaymentServiceImpl.verifyPayment()
    //   if (payment.getStatus().isFinalized()) {
    //       throw new PaymentException(ErrorCode.PAY_ALREADY_PROCESSED);
    //   }
    //
    // 시나리오: 프론트에서 네트워크 오류로 verifyPayment를 2번 호출
    //   1번째 호출 → PAID 처리 성공
    //   2번째 호출 → isFinalized() = true → PAY_ALREADY_PROCESSED 예외
    //
    // 이 테스트는 서비스 레이어 방어를 엔티티 도메인 메서드로 검증
    // (실제 PortOne API 호출 없이 markPaid() 상태 전이 로직만 검증)
    // ====================================================================

    @Test
    @Order(2)
    @DisplayName("1차 방어: 이미 PAID된 결제에 markPaid() 재호출 → IllegalStateException 발생해야 한다")
    void alreadyPaidPayment_markPaidAgain_shouldThrowException() {
        // given - READY 상태 Payment 저장 후 PAID로 전환
        Payment payment = buildPayment(TEST_MERCHANT_UID);
        paymentRepository.saveAndFlush(payment);
        payment.markPaid(); // 1번째 검증 완료 → PAID
        paymentRepository.saveAndFlush(payment);

        // when & then
        // 2번째 markPaid() 시도 → READY가 아니므로 예외
        assertThatThrownBy(() -> payment.markPaid())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("READY 상태에서만 결제 완료 처리할 수 있습니다");

        // PAID 상태 그대로 유지됐는지 확인 (변경되지 않아야 함)
        Payment checked = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(checked.getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    // ====================================================================
    // 테스트 3: 1차 방어 — isFinalized() 상태 체크 동작 검증
    // ====================================================================
    //
    // PaymentServiceImpl.verifyPayment()의 핵심 방어 조건:
    //   if (payment.getStatus().isFinalized()) → 처리 거부
    //
    // isFinalized() = true 조건: PAID 또는 CANCELLED
    // isFinalized() = false 조건: READY, FAILED
    // ====================================================================

    @Test
    @Order(3)
    @DisplayName("1차 방어: isFinalized() — PAID/CANCELLED는 true, READY/FAILED는 false여야 한다")
    void isFinalized_shouldReturnCorrectly() {
        // given
        Payment payment = buildPayment(TEST_MERCHANT_UID);
        paymentRepository.saveAndFlush(payment);

        // READY → isFinalized() = false (처리 가능)
        assertThat(payment.getStatus().isFinalized())
                .as("READY 상태는 처리 가능 → false")
                .isFalse();

        // PAID → isFinalized() = true (중복 처리 차단)
        payment.markPaid();
        assertThat(payment.getStatus().isFinalized())
                .as("PAID 상태는 이미 처리됨 → true")
                .isTrue();

        // CANCELLED → isFinalized() = true (중복 처리 차단)
        payment.markCancelled("테스트 취소");
        assertThat(payment.getStatus().isFinalized())
                .as("CANCELLED 상태는 이미 처리됨 → true")
                .isTrue();
    }

    // ====================================================================
    // 테스트 4: 2차 방어 — merchant_uid UNIQUE 제약
    // ====================================================================
    //
    // 방어 위치: Payment 엔티티 @Table uniqueConstraints
    //   @UniqueConstraint(name = "uk_payment_merchant_uid",
    //                     columnNames = {"merchant_uid"})
    //
    // 시나리오: 같은 merchant_uid로 Payment를 2번 생성 시도
    //   1번째 → 성공
    //   2번째 → DataIntegrityViolationException (UNIQUE 제약 위반)
    //
    // 이 방어가 왜 필요한가?
    //   generateMerchantUid()에서 countTodayAll() + 1로 순번을 채번하는데
    //   동시에 여러 결제가 들어오면 같은 순번이 나올 수 있음 (Race Condition)
    //   → UNIQUE 제약이 최후 방어선
    // ====================================================================

    @Test
    @Order(4)
    @DisplayName("2차 방어: 같은 merchant_uid로 Payment 중복 생성 → UNIQUE 제약으로 차단되어야 한다")
    void duplicateMerchantUid_shouldBeBlockedByUniqueConstraint() {
        // given - 첫 번째 Payment 저장
        Payment firstPayment = buildPayment(TEST_MERCHANT_UID);
        paymentRepository.saveAndFlush(firstPayment); // 성공

        // given - 같은 merchant_uid로 두 번째 Payment 생성 시도
        Payment duplicatePayment = buildPayment(TEST_MERCHANT_UID); // 같은 주문번호

        // when & then
        // merchant_uid UNIQUE 제약 위반 → DataIntegrityViolationException
        assertThatThrownBy(() -> paymentRepository.saveAndFlush(duplicatePayment))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_payment_merchant_uid"); // 제약 이름 포함 확인
    }

    // ====================================================================
    // 테스트 5: FAILED 상태는 isFinalized() = false — 재처리 가능
    // ====================================================================
    //
    // FAILED는 "결제가 완료되지 않은 것"이므로 isFinalized() = false
    // 사용자가 결제 실패 후 다시 시도하면 새 Payment를 만들어야 함
    // (같은 Payment를 READY로 되돌리는 게 아님)
    //
    // 이 테스트는 FAILED 상태가 중복 처리 차단 대상이 아님을 명시
    // ====================================================================

    @Test
    @Order(5)
    @DisplayName("FAILED 상태는 isFinalized() = false → 중복 처리 차단 대상이 아니다")
    void failedPayment_isNotFinalized() {
        // given
        Payment payment = buildPayment(TEST_MERCHANT_UID);
        paymentRepository.saveAndFlush(payment);
        payment.markFailed("PortOne API 호출 실패");
        paymentRepository.saveAndFlush(payment);

        // then
        // FAILED는 결제 미완료 → isFinalized() = false
        assertThat(payment.getStatus().isFinalized())
                .as("FAILED 상태는 결제 미완료 → 재시도 가능 → false")
                .isFalse();

        // FAILED 상태 확인
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);

        System.out.println("[안내] FAILED 상태 재시도는 새 Payment(새 merchant_uid)를 생성해야 함");
        System.out.println("       같은 Payment를 READY로 되돌리는 방식이 아님");
    }

    // ====================================================================
    // 테스트 6: READY 상태 만료 처리 — markFailed() 정상 동작 확인
    // ====================================================================
    //
    // 스케줄러(expireStaleReadyPayments())가 10분 이상 READY로 남은 건을
    // 자동으로 FAILED 처리하는 것을 검증
    // → READY → FAILED 상태 전이 + failReason 기록
    // ====================================================================

    @Test
    @Order(6)
    @DisplayName("READY → FAILED 상태 전이 → failReason 기록되어야 한다")
    void readyPayment_markFailed_shouldRecordFailReason() {
        // given
        Payment payment = buildPayment(TEST_MERCHANT_UID);
        paymentRepository.saveAndFlush(payment);

        String failReason = "결제 미완료 자동 만료 (30분 초과)";

        // when
        payment.markFailed(failReason);
        paymentRepository.saveAndFlush(payment);

        // then
        Payment saved = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(saved.getFailReason()).isEqualTo(failReason);
    }

    // ====================================================================
    // 헬퍼 메서드
    // ====================================================================

    /**
     * 테스트용 Payment 빌더 (READY 상태)
     * ChargePackage.P_3000 사용 (가장 작은 패키지)
     */
    private Payment buildPayment(String merchantUid) {
        return Payment.builder()
                .userId(TEST_USER_ID)
                .merchantUid(merchantUid)
                .chargePackage(ChargePackage.P_3000) // 실제 enum 이름 확인 필요
                .payMethod("card")
                .build();
    }
}
