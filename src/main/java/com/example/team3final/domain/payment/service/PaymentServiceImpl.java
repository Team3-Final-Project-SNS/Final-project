package com.example.team3final.domain.payment.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.PaymentException;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.payment.dto.request.CreatePaymentRequestDto;
import com.example.team3final.domain.payment.dto.request.VerifyPaymentRequestDto;
import com.example.team3final.domain.payment.dto.response.CancelPaymentResponseDto;
import com.example.team3final.domain.payment.dto.response.CreatePaymentResponseDto;
import com.example.team3final.domain.payment.dto.response.GetPaymentResponseDto;
import com.example.team3final.domain.payment.dto.response.VerifyPaymentResponseDto;
import com.example.team3final.domain.payment.entity.Payment;
import com.example.team3final.domain.payment.enums.ChargePackage;
import com.example.team3final.domain.payment.enums.PaymentStatus;
import com.example.team3final.domain.payment.repository.PaymentRepository;
import com.example.team3final.domain.user.service.UserPointService;
import io.portone.sdk.server.payment.PaidPayment;
import io.portone.sdk.server.payment.PaymentClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService{

    private final PaymentRepository paymentRepository;
    private final PaymentClient paymentClient;
    private final UserPointService userPointService;
    private final NotificationPublisher notificationPublisher;

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
            // PortOne API 호출 자체 실패
            payment.markFailed("PortOne API 호출 실패:" + e.getMessage());
            log.error("[Payment] PortOne 결제 조회 실패 - impUid: {}, error: {}",
                    request.getImpUid(), e.getMessage());
            throw new PaymentException(ErrorCode.PAY_VERIFICATION_FAILED);
        }

        // 4. 결제 상태 확인 — PortOne에서 PAID가 아니면 검증 실패
        //    SDK의 Payment는 sealed class: PaidPayment / FailedPayment 등으로 분기됨
        if (!(portOnePayment instanceof PaidPayment paidPayment)) {
            // 2. instanceof 분기 — PAID가 아닌 상태로 응답
            payment.markFailed("PortOne 결제 미완료: " +
                    portOnePayment.getClass().getSimpleName());
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
            payment.markFailed("금액 불일치 (위변조 감지) - 기대: " +
                    payment.getAmount() + "원, 실제: " + portOneAmount + "원");
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

        // 29. 결제 성공 알림 - 결제 사용자에게
        notificationPublisher.sendPaymentSuccess(userId, paymentId);

        return VerifyPaymentResponseDto.of(payment, request.getImpUid(), balanceAfter);
    }

    // 결제 내역 조회
    @Override
    @Transactional(readOnly = true)
    public PageResponseDto<GetPaymentResponseDto> getPayments(Long userId, Pageable pageable) {

        // userId 기준 최신순 페이징 조회 -> DTO로 변환
        // .map()으로 Page<Payment> -> Page<GetPaymentsResponseDto> 변환
        return PageResponseDto.from(
                paymentRepository
                        .findByUserIdOrderByCreatedAtDesc(userId, pageable)
                        .map(GetPaymentResponseDto::from)
        );
    }

    // 결제 취소
    @Override
    @Transactional
    public CancelPaymentResponseDto cancelPayment(Long userId, Long paymentId) {

        // 1. 결제 건 조회 — 없으면 PAY_NOT_FOUND
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentException(ErrorCode.PAY_NOT_FOUND));

        // 2. 본인 결제 건인지 확인 — 타인 결제 취소 시도 방지
        if (!payment.isOwner(userId)) {
            throw new PaymentException(ErrorCode.PAY_NOT_OWNER);
        }

        // 3. PAID 상태인지 확인
        //    PAID가 아니면 취소 불가 (READY/FAILED/CANCELLED)
        //    isFinalized() 대신 직접 체크 — FAILED도 isFinalized()에서 제외됐으므로 명시적으로 처리
        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new PaymentException(ErrorCode.PAY_ALREADY_PROCESSED);
        }

        // 4. 실제 회수 가능한 paidPoint 계산
        //    withdrawChargedPoint(): min(현재 paidPoint, 요청금액)만 회수
        //    → 이미 책임비로 사용된 포인트는 회수 불가 (SA 문서 정책)
        int actualWithdrawn = userPointService.withdrawChargedPoint(
                userId, payment.getAmount(), paymentId
        );

        // 5. 1,000원 단위 내림 — SA 문서 "1,000원 단위로만 환불 가능" 정책
        //    ex) actualWithdrawn = 4,500 → refundAmount = 4,000
        //    남은 500원은 환불되지 않음 (정책상 소멸)
        int refundAmount = (actualWithdrawn / 1000) * 1000;

        // 6. 환불 가능 금액이 0이면 PortOne 취소 API 호출 불필요
        //    (포인트를 전부 사용해서 환불할 현금이 없는 경우)
        if (refundAmount > 0) {
            try {
                // PortOne 부분 취소 API 호출
                // cancelPayment 파라미터 9개
                // - paymentId(merchantUid): 주문번호
                // - amount: 실제 환불할 금액 (Long 타입)
                // - taxFreeAmount: 면세 금액 — 포인트 충전은 해당 없으므로 null
                // - vatAmount: 부가세 — null (PortOne이 자동 계산)
                // - reason: 취소 사유 문자열
                // - requester ~ refundAccount: 선택값 전부 null
                paymentClient.cancelPayment(
                        payment.getMerchantUid(), // paymentId
                        (long) refundAmount,      // amount (int → Long 캐스팅)
                        null,                     // taxFreeAmount
                        null,                     // vatAmount
                        "사용자 취소 요청",        // reason
                        null,                     // requester
                        null,                     // promotionDiscountRetainOption
                        null,                     // currentCancellableAmount
                        null                      // refundAccount
                ).get();                          // CompletableFuture blocking 대기
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new PaymentException(ErrorCode.PAY_VERIFICATION_FAILED);
            } catch (Exception e) {
                log.error("[Payment] PortOne 취소 실패 - paymentId: {}, error: {}",
                        paymentId, e.getMessage());
                throw new PaymentException(ErrorCode.PAY_VERIFICATION_FAILED);
            }
        }

        // 7. DB 상태 CANCELLED 전환 + 취소 사유 기록
        payment.markCancelled("사용자 취소 요청 - 환불액: " + refundAmount + "원");

        // 31. 결제 취소 및 환불 완료 알림 - 결제 사용자에게
        notificationPublisher.sendPaymentCancelSuccess(userId, paymentId);

        log.info("[Payment] 결제 취소 완료 - userId: {}, paymentId: {}, refundAmount: {}",
                userId, paymentId, refundAmount);

        return CancelPaymentResponseDto.of(payment, refundAmount);
    }

    // 결제 실패 처리 (프론트 명시적 호출)
    @Override
    @Transactional
    public void failPayment(Long userId, Long paymentId) {

        // 결제 건 조회
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentException(ErrorCode.PAY_NOT_FOUND));

        // 본인 결제 건인지 확인
        if (!payment.isOwner(userId)) {
            throw new PaymentException(ErrorCode.PAY_NOT_OWNER);
        }

        // READY 상태가 아니면 처리 불필요
        // 이미 PAID/CANCELLED/FAILED면 무시 (멱등성 보장)
        if (payment.getStatus() != PaymentStatus.READY) {
            return;
        }

        // FAILED 처리
        payment.markFailed("사용자 결제 취소");

        // 30. 결제 실패 알림 - 결제 사용자에게
        notificationPublisher.sendPaymentFailed(userId, paymentId);

        log.info("[Payment] 결제 실패 처리 - userId: {}, paymentId: {}", userId, paymentId);
    }

    // 관리자 결제 내역 조회
    @Override
    @Transactional(readOnly = true)
    public Page<Payment> getPaymentsForAdmin(Long userId, PaymentStatus status, Pageable pageable) {
        return paymentRepository.findAllForAdmin(userId, status, pageable);
    }

    // ===== 스케줄러 — 오래된 READY 건 자동 만료 =====
    /**
     * 30분 이상 READY로 남아있는 결제 건을 FAILED로 일괄 처리
     * 주기: 매 5분마다 실행
     *
     * 보험 역할: 프론트가 failPayment 호출을 누락했을 때 자동으로 정리
     * ex) 네트워크 오류로 실패 API 못 보낸 경우, 브라우저 강제 종료 등
     */
    @Scheduled(cron = "0 */5 * * * *") // 매 5분마다 실행 (0초에 5분 간격)
    @Transactional
    public void expireStaleReadyPayments() {

        // 10분 전 시각 기준 - 그 이전에 생성된 READY 건이 만료 대상
        LocalDateTime expiredBefore = LocalDateTime.now().minusMinutes(10);

        List<Payment> stalePayments = paymentRepository.findExpiredReadyPayments(expiredBefore);

        if (stalePayments.isEmpty()) {
            return;
        }

        // 각 건마다 FAILED 처리
        stalePayments.forEach(payment ->
                payment.markFailed("결제 미완료 자동 만료 (30분 초과)"));

        log.info("[Payment] 자동 만료 처리 완료 - {}건 FAILED 전환", stalePayments.size());
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
