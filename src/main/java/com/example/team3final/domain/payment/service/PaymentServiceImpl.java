package com.example.team3final.domain.payment.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.PaymentException;
import com.example.team3final.domain.payment.dto.request.CreatePaymentRequestDto;
import com.example.team3final.domain.payment.dto.request.VerifyPaymentRequestDto;
import com.example.team3final.domain.payment.dto.response.CreatePaymentResponseDto;
import com.example.team3final.domain.payment.dto.response.VerifyPaymentResponseDto;
import com.example.team3final.domain.payment.entity.Payment;
import com.example.team3final.domain.payment.enums.ChargePackage;
import com.example.team3final.domain.payment.repository.PaymentRepository;
import com.example.team3final.domain.user.service.UserPointService;
import io.portone.sdk.server.payment.PaidPayment;
import io.portone.sdk.server.payment.PaymentClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService{

    private final PaymentRepository paymentRepository;
    private final PaymentClient paymentClient;
    private final UserPointService userPointService;

    // 결제 준비
    @Override
    public CreatePaymentResponseDto createPayment(Long userId, CreatePaymentRequestDto request) {

        // 1. 패키지 검증 - 3000/5000/10000/20000 외 값이면 PAY_MIN_CHARGE 예외
        //    ChargePackage.fromPoint() 내부에서 예외를 던지므로 별도 if 불필요
        ChargePackage chargePackage = ChargePackage.fromPoint(request.getChargePoint());

        // 2. merchant_uid 채번
        // 형태: hankki_20260601_000001
        // 오늘 전체 결제 건수 + 1을 6자리 제로패딩으로 붙임
        String merchantUid = generateMerchantUid();

        // 3. Payment 엔티티 생성 - READY 상태로 DB 저장
        //    Payment.builder()가 내부에서 chargePoint, amount를 패키지에서 스냅샷으로 뽑음
        Payment payment = Payment.builder()
                .userId(userId)
                .merchantUid(merchantUid)
                .chargePackage(chargePackage)
                .payMethod(request.getPayMethod())
                .build();

        Payment saved = paymentRepository.save(payment);

        log.info("[Payment] 결제 준비 완료 - userId: {}, merchantUid: {}, amount: {}",
                userId, merchantUid, saved.getAmount());

        return CreatePaymentResponseDto.from(saved);
    }

    // 결제 완료 검증
    @Override
    @Transactional
    public VerifyPaymentResponseDto verifyPayment(Long userId, Long paymentId,
                                                  VerifyPaymentRequestDto request) {
        // 1. 결제 건 조회 - 없으면 PAY_003
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentException(ErrorCode.PAY_NOT_FOUND));

        // 2. 이미 처리된 결제인지 확인 (PAID or CANCELLED) - 중복 검증 방지
        // isFinalized(): PaymentStatus.PAID || CANCELLED 이면 true
        if (payment.getStatus().isFinalized()) {
            throw new PaymentException(ErrorCode.PAY_ALREADY_PROCESSED);
        }

        // 3. PortOne API 호출
        //    getPayment()가 CompletableFuture<Payment>를 반환하므로 .get()으로 blocking 대기
        //    InterruptedException, ExecutionException 두 가지 checked exception 처리 필요
        io.portone.sdk.server.payment.Payment portOnePayment;
        try {
            portOnePayment = paymentClient.getPayment(request.getImpUid()).get();
        } catch (InterruptedException e) {
            // 대기 중 스레드가 인터럽트된 경우 — 스레드 상태 복구 후 예외 전환
            Thread.currentThread().interrupt();
            throw new PaymentException(ErrorCode.PAY_VERIFICATION_FAILED);
        } catch (Exception e) {
            log.error("[Payment] PortOne 결제 조회 실패 - impUid: {}, error: {}",
                    request.getImpUid(), e.getMessage());
            throw new PaymentException(ErrorCode.PAY_VERIFICATION_FAILED);
        }

        // 4. 결제 상태 확인 — PortOne에서 PAID가 아니면 검증 실패
        //    SDK의 Payment는 sealed class: PaidPayment / FailedPayment 등으로 분기됨
        if (!(portOnePayment instanceof PaidPayment paidPayment)) {
            log.warn("[Payment] PortOne 결제 미완료 상태 - impUid: {}, status: {}",
                    request.getImpUid(), portOnePayment.getClass().getSimpleName());
            throw new PaymentException(ErrorCode.PAY_VERIFICATION_FAILED);
        }

        // 5. 금액 검증 - DB 저장 금액 vs PortOne 실제 결제 금액 비교
        //    totalAmount: PaidPayment에서만 꺼낼 수 있는 실제 결제 금액(원)
        //    일치하지 않으면 위변조 시도 -> PAY_004
        int portOneAmount = (int) paidPayment.getAmount().getTotal();
        if (payment.getAmount() != portOneAmount) {
            // 위변조 감지 - 결제 실패 처리 후 예외
            payment.markFailed();
            log.warn("[Payment] 금액 불일치 위변조 감지 - paymentId: {}, 기대: {}, 실제: {}",
                    paymentId, payment.getAmount(),portOneAmount);
            throw new PaymentException(ErrorCode.PAY_AMOUNT_MISMATCH);
        }

        // 6. 결제 완료 상태 전환 - READY -> PAID, completedAt 세팅
        payment.markPaid();

        // 7. 유료 포인트 지급 + 잔액 반환
        //    chargePoint()가 addPaidPoint() + 거래 내역 기록 + 잔액 반환까지 한 번에 처리
        int balanceAfter = userPointService.chargePoint(userId, payment.getChargePoint(), paymentId);

        log.info("[Payment] 결제 검증 완료 - userId: {}, paymentId: {}, chargePoint: {}",
                userId, paymentId, payment.getChargePoint());

        return VerifyPaymentResponseDto.of(payment, request.getImpUid(), balanceAfter);
    }

    // ===== private 헬퍼 =====

    /**
     * merchant_uid 채번
     *
     * 형태: hankki_20260601_000003
     *   - hankki_  : 서비스 식별자
     *   - 20260601 : 오늘 날짜 (yyyyMMdd)
     *   - 000003   : 오늘 전체 누적 결제 건수 + 1, 6자리 제로패딩
     *
     * 왜 전체 카운트를 쓰나:
     *   유저별 카운트는 같은 날 여러 유저가 동시에 1번째 결제를 하면
     *   동일한 merchant_uid가 나올 수 있음 → 전체 카운트로 전역 순번 보장
     *   Payment 테이블에 merchant_uid UNIQUE 제약이 있으므로
     *   극히 드문 동시성 충돌은 DB에서 최종 차단됨
     */
    private String generateMerchantUid() {

        // 매일 자정
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();

        // 당일 생성된 전체 결제 건수
        long todayCount = paymentRepository.countTodayAll(startOfDay);

        // 날짜 포맷 - LocalDate.toString()은 "2026-06-01" 형태이므로 "-" 제거
        String date = LocalDate.now().toString().replace("-","");

        // 순번: 현재 건수 + 1, 6자리 제로패딩
        String sequence = String.format("%06d", todayCount + 1);

        return "hankki_" + date + "_" + sequence;
    }
}
