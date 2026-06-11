package com.example.team3final.domain.pointTransaction.controller;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.pointTransaction.dto.response.PointTransactionResponseDto;
import com.example.team3final.domain.pointTransaction.service.PointTransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.example.team3final.test.security.WithMockCustomUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PointTransactionController.class)
@AutoConfigureMockMvc(addFilters = false)
class PointTransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PointTransactionService pointTransactionService;

    @Test
    @DisplayName("API test")
    @WithMockCustomUser
    void getPointTransactions_ApiTest() throws Exception {
        // given
        PageResponseDto<PointTransactionResponseDto> response =
                PageResponseDto.from(new PageImpl<>(List.<PointTransactionResponseDto>of()));

        given(pointTransactionService.getPointTransactions(nullable(String.class), any(), any()))
                .willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/me/points/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }
}
