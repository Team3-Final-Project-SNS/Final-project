package com.example.team3final.domain.payment.service;

import com.example.team3final.domain.payment.entity.Payment;
import com.example.team3final.domain.payment.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

// Payment 도메인의 관리자 조회 및 내부 결제 처리 기능을 제공하는 서비스
public interface PaymentInternalService {

    // 관리자 결제 내역 조회
    Page<Payment> getPaymentsForAdmin(
            Long userId,
            PaymentStatus status,
            Pageable pageable
    );
}
