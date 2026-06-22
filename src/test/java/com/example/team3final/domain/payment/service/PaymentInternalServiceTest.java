package com.example.team3final.domain.payment.service;

import com.example.team3final.domain.payment.enums.PaymentStatus;
import com.example.team3final.domain.payment.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentInternalService 단위 테스트")
class PaymentInternalServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentInternalServiceImpl paymentInternalService;

    @Test
    @DisplayName("관리자 결제 목록 조회는 사용자와 상태 필터를 저장소에 위임한다")
    void getPaymentsForAdmin_shouldDelegateWithFilters() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(paymentRepository.findAllForAdmin(1L, PaymentStatus.READY, pageable)).thenReturn(Page.empty(pageable));

        paymentInternalService.getPaymentsForAdmin(1L, PaymentStatus.READY, pageable);

        verify(paymentRepository).findAllForAdmin(1L, PaymentStatus.READY, pageable);
    }

    @Test
    @DisplayName("만료된 준비 상태 결제가 없으면 자동 만료 처리를 하지 않는다")
    void expireStaleReadyPayments_shouldDoNothingWhenEmpty() {
        when(paymentRepository.findExpiredReadyPayments(any())).thenReturn(List.of());

        paymentInternalService.expireStaleReadyPayments();

        verify(paymentRepository).findExpiredReadyPayments(any());
    }
}
