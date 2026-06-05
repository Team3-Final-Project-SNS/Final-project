package com.example.team3final.domain.admin.payment.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.AdminException;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.payment.dto.response.AdminGetPaymentsResponseDto;
import com.example.team3final.domain.admin.repository.AdminRepository;
import com.example.team3final.domain.payment.entity.Payment;
import com.example.team3final.domain.payment.enums.PaymentStatus;
import com.example.team3final.domain.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPaymentServiceImpl implements AdminPaymentService {

    private final AdminRepository adminRepository;
    private final PaymentService paymentService;

    // 관리자 결제내역 목록 조회
    @Override
    public PageResponseDto<AdminGetPaymentsResponseDto> getPayments(
            Long adminId,
            Long userId,
            PaymentStatus status,
            Pageable pageable) {

        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new AdminException(ErrorCode.ADMIN_NOT_FOUND));

        // 활성화된 관리자인지 확인
        if (!admin.isActiveAndSuperAdmin()) {
            throw new AdminException(ErrorCode.ADMIN_SUPER_REQUIRED);
        }

        // 조건에 맞는 결제내역 Page 조회
        // userId가 null이면 전체 유저 대상
        // status가 null이면 전체 상태 대상
        Page<Payment> payments = paymentService.getPaymentsForAdmin(userId, status, pageable);

        // payments를 AdminGetPaymentsResponseDto 타입으로 변환
        Page<AdminGetPaymentsResponseDto> response = payments.map(AdminGetPaymentsResponseDto::from);

        // DTO 변환
        return PageResponseDto.from(response);
    }
}

