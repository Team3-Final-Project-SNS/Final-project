package com.example.team3final.domain.payment.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.PaymentException;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.payment.dto.request.CreatePaymentRequestDto;
import com.example.team3final.domain.payment.dto.response.CreatePaymentResponseDto;
import com.example.team3final.domain.payment.dto.response.GetPaymentResponseDto;
import com.example.team3final.domain.payment.entity.Payment;
import com.example.team3final.domain.payment.enums.ChargePackage;
import com.example.team3final.domain.payment.enums.PaymentStatus;
import com.example.team3final.domain.payment.repository.PaymentRepository;
import com.example.team3final.domain.user.service.UserPointService;
import io.portone.sdk.server.payment.PaymentClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentCommandService 단위 테스트")
class PaymentCommandServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private UserPointService userPointService;

    @Mock
    private NotificationPublisher notificationPublisher;

    @Mock
    private PaymentClient paymentClient;

    @InjectMocks
    private PaymentCommandServiceImpl paymentCommandService;

    @Test
    @DisplayName("결제 생성은 충전 패키지를 검증하고 READY 결제를 저장한다")
    void createPayment_shouldSaveReadyPayment() {
        CreatePaymentRequestDto request = new CreatePaymentRequestDto();
        ReflectionTestUtils.setField(request, "chargePoint", 3000);
        ReflectionTestUtils.setField(request, "payMethod", "CARD");
        when(paymentRepository.countTodayAll(any())).thenReturn(0L);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            ReflectionTestUtils.setField(payment, "id", 1L);
            return payment;
        });

        CreatePaymentResponseDto result = paymentCommandService.createPayment(10L, request);

        assertThat(result.paymentId()).isEqualTo(1L);
        assertThat(result.status()).isEqualTo(PaymentStatus.READY);
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("결제 내역 조회는 사용자 ID 기준 최신순 결제 페이지를 반환한다")
    void getPayments_shouldReturnUserPayments() {
        PageRequest pageable = PageRequest.of(0, 10);
        Payment payment = payment(1L, 10L, PaymentStatus.READY);
        when(paymentRepository.findByUserIdOrderByCreatedAtDesc(10L, pageable))
                .thenReturn(new PageImpl<>(List.of(payment), pageable, 1));

        PageResponseDto<GetPaymentResponseDto> result = paymentCommandService.getPayments(10L, pageable);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).paymentId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("결제 실패 처리는 READY 상태 결제를 FAILED로 변경하고 실패 알림을 발송한다")
    void failPayment_shouldMarkFailedAndSendNotification() {
        Payment payment = payment(1L, 10L, PaymentStatus.READY);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        paymentCommandService.failPayment(10L, 1L);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(notificationPublisher).sendPaymentFailed(10L, 1L);
    }

    @Test
    @DisplayName("결제 실패 처리는 다른 사용자의 결제이면 결제 예외를 던진다")
    void failPayment_shouldThrowWhenNotOwner() {
        Payment payment = payment(1L, 99L, PaymentStatus.READY);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentCommandService.failPayment(10L, 1L))
                .isInstanceOf(PaymentException.class);
    }

    private Payment payment(Long paymentId, Long userId, PaymentStatus status) {
        Payment payment = Payment.builder()
                .userId(userId)
                .merchantUid("merchant-uid")
                .chargePackage(ChargePackage.P_3000)
                .payMethod("CARD")
                .build();
        ReflectionTestUtils.setField(payment, "id", paymentId);
        ReflectionTestUtils.setField(payment, "status", status);
        return payment;
    }
}
