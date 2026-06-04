package com.example.team3final.domain.payment.repository;

import com.example.team3final.domain.payment.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * merchant_uid 채번용 — 오늘 전체 결제 건수 카운트
     *
     * 오늘 자정(00:00:00) 이후 생성된 전체 결제 건수를 세서
     * 순번 패딩에 사용 (hankki_20260601_000003 형태)
     */
    @Query("""
            SELECT COUNT(p) FROM Payment p
            WHERE p.createdAt >= :startOfDay
            """)
    long countTodayAll(@Param("startOfDay")LocalDateTime startOfDay);

    // 내 결제 내역 최신순 페이징 조회
    Page<Payment> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * 오래된 READY 건 조회 — 스케줄러 만료 처리용
     *
     * 조건: READY 상태 + 생성 시각이 기준 시각보다 과거
     * ex) 30분 이상 READY로 남아있는 건 전부
     */
    @Query("""
        SELECT p FROM Payment p
        WHERE p.status = 'READY'
          AND p.createdAt < :expiredBefore
        """)
    List<Payment> findExpiredReadyPayments(@Param("expiredBefore") LocalDateTime expiredBefore);
}
