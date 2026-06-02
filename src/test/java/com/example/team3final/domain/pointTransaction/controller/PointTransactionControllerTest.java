package com.example.team3final.domain.pointTransaction.controller;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.pointTransaction.service.PointTransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PointTransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PointTransactionService pointTransactionService;

    @Test
    @DisplayName("포인트 거래 내역 조회 - 성공")
    @WithMockUser(username = "test@test.ac.kr")
    void getPointTransactions_Success() throws Exception {
        // given
        PageResponseDto responseDto = new PageResponseDto(Collections.emptyList(), 0, 20, 0, 0, false);
        given(pointTransactionService.getPointTransactions(anyString(), any(), any())).willReturn(responseDto);

        // when & then
        mockMvc.perform(get("/api/v1/me/points/transactions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}

