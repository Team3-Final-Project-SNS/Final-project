package com.example.team3final.domain.payment.controller;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.payment.dto.request.CreatePaymentRequestDto;
import com.example.team3final.domain.payment.dto.request.VerifyPaymentRequestDto;
import com.example.team3final.domain.payment.service.PaymentCommandService;
import com.example.team3final.test.controller.ControllerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("결제 컨트롤러 통합 테스트")
class PaymentControllerTest extends ControllerTestSupport {

    @Mock
    private PaymentCommandService paymentCommandService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvcFor(new PaymentController(paymentCommandService));
    }

    @Test
    @DisplayName("결제 준비 API는 사용자 ID와 결제 요청을 서비스로 전달하고 201을 반환한다")
    void createPayment_shouldReturnCreatedAndDelegate() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .with(authentication(userAuthentication(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "chargePoint": 3000,
                                  "payMethod": "card"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        verify(paymentCommandService).createPayment(eq(1L), any(CreatePaymentRequestDto.class));
    }

    @Test
    @DisplayName("결제 검증 API는 결제 ID와 impUid 요청을 서비스로 전달한다")
    void verifyPayment_shouldDelegatePaymentVerification() throws Exception {
        mockMvc.perform(post("/api/v1/payments/10/verify")
                        .with(authentication(userAuthentication(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"impUid\":\"imp_123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(paymentCommandService).verifyPayment(eq(1L), eq(10L), any(VerifyPaymentRequestDto.class));
    }

    @Test
    @DisplayName("내 결제 목록 조회 API는 페이지 크기를 최대 50으로 제한한다")
    void getPayments_shouldClampPageSize() throws Exception {
        when(paymentCommandService.getPayments(eq(1L), any(Pageable.class)))
                .thenReturn(new PageResponseDto<>(List.of(), 1, 50, 0, 0, false));

        mockMvc.perform(get("/api/v1/payments/me")
                        .with(authentication(userAuthentication(1L)))
                        .param("page", "1")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(paymentCommandService).getPayments(eq(1L), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    @DisplayName("결제 취소 API는 사용자 ID와 결제 ID를 서비스로 전달한다")
    void cancelPayment_shouldDelegateUserAndPaymentId() throws Exception {
        mockMvc.perform(patch("/api/v1/payments/10/cancel")
                        .with(authentication(userAuthentication(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(paymentCommandService).cancelPayment(1L, 10L);
    }

    @Test
    @DisplayName("결제 실패 API는 사용자 ID와 결제 ID를 서비스로 전달하고 성공 응답을 반환한다")
    void failPayment_shouldDelegateAndReturnNoContentSuccess() throws Exception {
        mockMvc.perform(patch("/api/v1/payments/10/fail")
                        .with(authentication(userAuthentication(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(paymentCommandService).failPayment(1L, 10L);
    }
}
