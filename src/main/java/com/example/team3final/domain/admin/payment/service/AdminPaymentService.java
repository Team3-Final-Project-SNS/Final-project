package com.example.team3final.domain.admin.payment.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.admin.payment.dto.response.AdminGetPaymentsResponseDto;
import com.example.team3final.domain.payment.enums.PaymentStatus;
import org.springframework.data.domain.Pageable;

public interface AdminPaymentService {

    // 관리자 결제내역 목록 조회
    PageResponseDto<AdminGetPaymentsResponseDto> getPayments(
            Long adminId, Long userId, PaymentStatus status, Pageable pageable);
}
