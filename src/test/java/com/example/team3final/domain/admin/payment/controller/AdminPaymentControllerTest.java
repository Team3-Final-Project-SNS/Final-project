package com.example.team3final.domain.admin.payment.controller;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.admin.payment.dto.response.AdminGetPaymentsResponseDto;
import com.example.team3final.domain.admin.payment.service.AdminPaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.example.team3final.test.security.WithMockAdmin;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminPaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminPaymentService adminPaymentService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("관리자 결제 내역 조회 API test")
    @WithMockAdmin
    void getPayments_ApiTest() throws Exception {
        // given
        PageResponseDto<AdminGetPaymentsResponseDto> response =
                PageResponseDto.from(new PageImpl<>(List.of()));

        given(adminPaymentService.getPayments(anyLong(), any(), any(), any()))
                .willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/admin/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }
}
