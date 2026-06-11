package com.example.team3final.domain.admin.payment.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.payment.dto.response.AdminGetPaymentsResponseDto;
import com.example.team3final.domain.admin.repository.AdminRepository;
import com.example.team3final.domain.payment.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AdminPaymentServiceTest {

    @InjectMocks
    private AdminPaymentServiceImpl adminPaymentService;

    @Mock
    private AdminRepository adminRepository;
    @Mock
    private PaymentService paymentService;

    @Test
    @DisplayName("관리자 결제내역 목록 조회 - 성공 (빈 목록)")
    void getPayments_Empty_Success() {
        // given
        Long adminId = 1L;
        Pageable pageable = PageRequest.of(0, 10);
        Admin admin = mock(Admin.class);
        given(admin.isActiveAndSuperAdmin()).willReturn(true);
        given(adminRepository.findById(adminId)).willReturn(Optional.of(admin));
        given(paymentService.getPaymentsForAdmin(any(), any(), any())).willReturn(new PageImpl<>(List.of()));

        // when
        PageResponseDto<AdminGetPaymentsResponseDto> result = adminPaymentService.getPayments(adminId, null, null, pageable);

        // then
        assertThat(result.content()).isEmpty();
    }
}
