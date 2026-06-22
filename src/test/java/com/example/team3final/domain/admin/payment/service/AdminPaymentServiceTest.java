package com.example.team3final.domain.admin.payment.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.AdminException;
import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.enums.AdminRole;
import com.example.team3final.domain.admin.payment.dto.response.AdminGetPaymentsResponseDto;
import com.example.team3final.domain.admin.repository.AdminRepository;
import com.example.team3final.domain.payment.entity.Payment;
import com.example.team3final.domain.payment.enums.PaymentStatus;
import com.example.team3final.domain.payment.service.PaymentInternalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("관리자 결제 서비스 단위 테스트")
class AdminPaymentServiceTest {

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private PaymentInternalService paymentInternalService;

    @InjectMocks
    private AdminPaymentServiceImpl adminPaymentService;

    @Test
    @DisplayName("관리자 결제 목록 조회는 관리자 검증 후 결제 내부 서비스 결과를 페이지 응답으로 반환한다")
    void getPayments_shouldReturnPayments() {
        PageRequest pageable = PageRequest.of(0, 10);
        Admin admin = Admin.createAdmin("admin@test.com", "password", "관리자", AdminRole.SUPER_ADMIN);
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(paymentInternalService.getPaymentsForAdmin(2L, PaymentStatus.PAID, pageable))
                .thenReturn(new PageImpl<Payment>(List.of(), pageable, 0));

        PageResponseDto<AdminGetPaymentsResponseDto> result =
                adminPaymentService.getPayments(1L, 2L, PaymentStatus.PAID, pageable);

        assertThat(result.content()).isEmpty();
        verify(paymentInternalService).getPaymentsForAdmin(2L, PaymentStatus.PAID, pageable);
    }

    @Test
    @DisplayName("관리자 결제 목록 조회는 관리자가 없으면 관리자 예외를 던진다")
    void getPayments_shouldThrowWhenAdminNotFound() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(adminRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminPaymentService.getPayments(1L, null, null, pageable))
                .isInstanceOf(AdminException.class);
    }
}
