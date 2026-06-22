package com.example.team3final.domain.admin.payment.controller;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.admin.payment.service.AdminPaymentService;
import com.example.team3final.domain.payment.enums.PaymentStatus;
import com.example.team3final.test.controller.ControllerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("관리자 결제 컨트롤러 통합 테스트")
class AdminPaymentControllerTest extends ControllerTestSupport {

    @Mock
    private AdminPaymentService adminPaymentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvcFor(new AdminPaymentController(adminPaymentService));
    }

    @Test
    @DisplayName("관리자 결제 목록 조회 API는 필터를 바인딩하고 페이지 크기를 최대 50으로 제한한다")
    void getPayments_shouldClampPageSizeAndDelegateToService() throws Exception {
        when(adminPaymentService.getPayments(eq(1L), eq(2L), eq(PaymentStatus.PAID), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageResponseDto<>(List.of(), 1, 50, 0, 0, false));

        mockMvc.perform(get("/api/v1/admin/payments")
                        .with(authentication(adminAuthentication(1L)))
                        .param("userId", "2")
                        .param("status", "PAID")
                        .param("page", "1")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(adminPaymentService).getPayments(eq(1L), eq(2L), eq(PaymentStatus.PAID), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(50);
    }
}
