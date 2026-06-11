package com.example.team3final.domain.payment.controller;

import com.example.team3final.domain.payment.dto.request.CreatePaymentRequestDto;
import com.example.team3final.domain.payment.dto.request.VerifyPaymentRequestDto;
import com.example.team3final.domain.payment.dto.response.CreatePaymentResponseDto;
import com.example.team3final.domain.payment.enums.PaymentStatus;
import com.example.team3final.domain.payment.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import com.example.team3final.test.security.WithMockCustomUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("API test")
    @WithMockCustomUser
    void createPayment_ApiTest() throws Exception {
        // given
        CreatePaymentRequestDto request = new CreatePaymentRequestDto();
        ReflectionTestUtils.setField(request, "chargePoint", 3000);
        ReflectionTestUtils.setField(request, "payMethod", "CARD");

        CreatePaymentResponseDto response = new CreatePaymentResponseDto(1L, "merchant_uid", 3000, 3000, PaymentStatus.READY, LocalDateTime.now());

        given(paymentService.createPayment(anyLong(), any())).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("API test")
    @WithMockCustomUser
    void verifyPayment_ApiTest() throws Exception {
        // given
        VerifyPaymentRequestDto request = new VerifyPaymentRequestDto();
        ReflectionTestUtils.setField(request, "impUid", "imp_uid");
        given(paymentService.verifyPayment(anyLong(), anyLong(), any())).willReturn(null);

        // when & then
        mockMvc.perform(post("/api/v1/payments/{paymentId}/verify", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("API test")
    @WithMockCustomUser
    void getPayments_ApiTest() throws Exception {
        // given
        given(paymentService.getPayments(anyLong(), any())).willReturn(null);

        // when & then
        mockMvc.perform(get("/api/v1/payments/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("API test")
    @WithMockCustomUser
    void cancelPayment_ApiTest() throws Exception {
        // given
        given(paymentService.cancelPayment(anyLong(), anyLong())).willReturn(null);

        // when & then
        mockMvc.perform(patch("/api/v1/payments/{paymentId}/cancel", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("API test")
    @WithMockCustomUser
    void failPayment_ApiTest() throws Exception {
        // given
        doNothing().when(paymentService).failPayment(anyLong(), anyLong());

        // when & then
        mockMvc.perform(patch("/api/v1/payments/{paymentId}/fail", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }
}
