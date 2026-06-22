package com.example.team3final.domain.payment.service;

import com.example.team3final.domain.payment.entity.Payment;
import com.example.team3final.domain.payment.enums.PaymentStatus;
import com.example.team3final.domain.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// Payment 도메인의 관리자 조회 및 내부 결제 처리 기능을 제공하는 서비스
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class PaymentInternalServiceImpl implements PaymentInternalService {

    private final PaymentRepository paymentRepository;

    // 관리자 결제 내역 조회
    @Override
    public Page<Payment> getPaymentsForAdmin(Long userId, PaymentStatus status, Pageable pageable) {
        return paymentRepository.findAllForAdmin(userId, status, pageable);
    }

    // ===== 스케줄러 — 오래된 READY 건 자동 만료 =====
    /**
     * 30분 이상 READY로 남아있는 결제 건을 FAILED로 일괄 처리
     * 주기: 매 5분마다 실행
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
                payment.markFailed("결제 미완료 자동 만료 (10분 초과)"));

        log.info("[Payment] 자동 만료 처리 완료 - {}건 FAILED 전환", stalePayments.size());
    }
}
