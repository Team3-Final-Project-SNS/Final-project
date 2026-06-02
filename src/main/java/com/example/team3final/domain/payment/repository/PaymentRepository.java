package com.example.team3final.domain.payment.repository;

import com.example.team3final.domain.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

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
}
