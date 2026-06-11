package com.example.team3final.domain.payment.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.payment.dto.request.CreatePaymentRequestDto;
import com.example.team3final.domain.payment.dto.request.VerifyPaymentRequestDto;
import com.example.team3final.domain.payment.dto.response.CancelPaymentResponseDto;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @InjectMocks
    private PaymentServiceImpl paymentService;

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentClient paymentClient;
    @Mock
    private UserPointService userPointService;
    @Mock
    private NotificationPublisher notificationPublisher;

    @Test
    @DisplayName("결제 준비 - 성공")
    void createPayment_Success() {
        // given
        Long userId = 1L;
        CreatePaymentRequestDto request = new CreatePaymentRequestDto();
        ReflectionTestUtils.setField(request, "chargePoint", 3000);
        ReflectionTestUtils.setField(request, "payMethod", "CARD");

        given(paymentRepository.countTodayAll(any(LocalDateTime.class))).willReturn(0L);

        Payment payment = Payment.builder()
                .userId(userId)
                .merchantUid("hankki_20260611_000001")
                .chargePackage(ChargePackage.P_3000)
                .payMethod("CARD")
                .build();
        ReflectionTestUtils.setField(payment, "id", 1L);
        given(paymentRepository.save(any(Payment.class))).willReturn(payment);

        // when
        CreatePaymentResponseDto result = paymentService.createPayment(userId, request);

        // then
        assertThat(result.merchantUid()).isEqualTo("hankki_20260611_000001");
        assertThat(result.chargePoint()).isEqualTo(3000);
        assertThat(result.amount()).isEqualTo(3000);
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("결제 검증 - 이미 처리된 결제")
    void verifyPayment_AlreadyProcessed_ThrowsException() {
        Payment payment = createPaymentEntity(1L, 1L);
        payment.markPaid();
        VerifyPaymentRequestDto request = new VerifyPaymentRequestDto();
        ReflectionTestUtils.setField(request, "impUid", "imp-uid");
        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.verifyPayment(1L, 1L, request))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("결제 내역 조회 - 성공")
    void getPayments_Success() {
        PageRequest pageable = PageRequest.of(0, 10);
        Payment payment = createPaymentEntity(1L, 1L);
        given(paymentRepository.findByUserIdOrderByCreatedAtDesc(1L, pageable))
                .willReturn(new PageImpl<>(List.of(payment), pageable, 1));

        PageResponseDto<GetPaymentResponseDto> result = paymentService.getPayments(1L, pageable);

        assertThat(result.content()).hasSize(1);
    }

    @Test
    @DisplayName("결제 취소 - 성공")
    void cancelPayment_Success() {
        Payment payment = createPaymentEntity(1L, 1L);
        payment.markPaid();
        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
        given(userPointService.withdrawChargedPoint(1L, 3000, 1L)).willReturn(0);

        CancelPaymentResponseDto result = paymentService.cancelPayment(1L, 1L);

        assertThat(result.paymentId()).isEqualTo(1L);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(notificationPublisher).sendPaymentCancelSuccess(1L, 1L);
    }

    @Test
    @DisplayName("결제 실패 처리 - 성공")
    void failPayment_Success() {
        Payment payment = createPaymentEntity(1L, 1L);
        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));

        paymentService.failPayment(1L, 1L);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(notificationPublisher).sendPaymentFailed(1L, 1L);
    }

    @Test
    @DisplayName("관리자 결제 목록 조회 - 성공")
    void getPaymentsForAdmin_Success() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Payment> page = new PageImpl<>(List.of(createPaymentEntity(1L, 1L)));
        given(paymentRepository.findAllForAdmin(1L, PaymentStatus.READY, pageable)).willReturn(page);

        Page<Payment> result = paymentService.getPaymentsForAdmin(1L, PaymentStatus.READY, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("오래된 READY 결제 만료 - 성공")
    void expireStaleReadyPayments_Success() {
        Payment payment = createPaymentEntity(1L, 1L);
        given(paymentRepository.findExpiredReadyPayments(any(LocalDateTime.class))).willReturn(List.of(payment));

        paymentService.expireStaleReadyPayments();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    private Payment createPaymentEntity(Long id, Long userId) {
        Payment payment = Payment.builder()
                .userId(userId)
                .merchantUid("merchant-" + id)
                .chargePackage(ChargePackage.P_3000)
                .payMethod("CARD")
                .build();
        ReflectionTestUtils.setField(payment, "id", id);
        ReflectionTestUtils.setField(payment, "createdAt", LocalDateTime.now());
        return payment;
    }
}
