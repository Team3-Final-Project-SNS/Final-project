package com.example.team3final.domain.pointTransaction.controller;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.pointTransaction.enums.PointTransactionType;
import com.example.team3final.domain.pointTransaction.service.PointTransactionService;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("포인트 거래 내역 컨트롤러 통합 테스트")
class PointTransactionControllerTest extends ControllerTestSupport {

    @Mock
    private PointTransactionService pointTransactionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvcFor(new PointTransactionController(pointTransactionService));
    }

    @Test
    @DisplayName("내 포인트 거래 내역 조회 API는 인증 이메일, 거래 타입, 페이징 조건을 서비스로 전달한다")
    void getPointTransactions_shouldDelegateEmailTypeAndPageable() throws Exception {
        when(pointTransactionService.getPointTransactions(eq("user1@test.ac.kr"), eq(PointTransactionType.CHARGE), any(Pageable.class)))
                .thenReturn(new PageResponseDto<>(List.of(), 0, 20, 0, 0, false));

        mockMvc.perform(get("/api/v1/me/points/transactions")
                        .principal(userAuthentication(1L))
                        .param("type", "CHARGE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(pointTransactionService).getPointTransactions(eq("user1@test.ac.kr"), eq(PointTransactionType.CHARGE), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
    }
}
