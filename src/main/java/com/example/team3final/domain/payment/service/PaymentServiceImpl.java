package com.example.team3final.domain.payment.service;

import com.example.team3final.domain.payment.dto.request.CreatePaymentRequestDto;
import com.example.team3final.domain.payment.dto.response.CreatePaymentResponseDto;
import com.example.team3final.domain.payment.entity.Payment;
import com.example.team3final.domain.payment.enums.ChargePackage;
import com.example.team3final.domain.payment.repository.PaymentRepository;
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
