package com.example.team3final.domain.admin.dispute.controller;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.admin.dispute.dto.request.AdminJudgeDisputeRequestDto;
import com.example.team3final.domain.admin.dispute.dto.request.AdminOverrideDisputeStatusRequestDto;
import com.example.team3final.domain.admin.dispute.service.AdminDisputeService;
import com.example.team3final.domain.dispute.enums.DisputeStatus;
import com.example.team3final.test.controller.ControllerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("관리자 이의제기 컨트롤러 통합 테스트")
class AdminDisputeControllerTest extends ControllerTestSupport {

    @Mock
    private AdminDisputeService adminDisputeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvcFor(new AdminDisputeController(adminDisputeService));
    }

    @Test
    @DisplayName("관리자 이의제기 상세 조회 API는 관리자 ID와 이의제기 ID를 서비스로 전달한다")
    void getDispute_shouldDelegateAdminIdAndDisputeId() throws Exception {
        mockMvc.perform(get("/api/v1/admin/disputes/10")
                        .with(authentication(adminAuthentication(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(adminDisputeService).getDispute(1L, 10L);
    }

    @Test
    @DisplayName("관리자 이의제기 목록 조회 API는 상태 필터와 페이징 조건을 서비스로 전달한다")
    void getDisputes_shouldDelegateStatusAndPageable() throws Exception {
        when(adminDisputeService.getDisputes(eq(1L), eq(DisputeStatus.SUBMITTED), any(Pageable.class)))
                .thenReturn(new PageResponseDto<>(List.of(), 0, 20, 0, 0, false));

        mockMvc.perform(get("/api/v1/admin/disputes")
                        .with(authentication(adminAuthentication(1L)))
                        .param("status", "SUBMITTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(adminDisputeService).getDisputes(eq(1L), eq(DisputeStatus.SUBMITTED), any(Pageable.class));
    }

    @Test
    @DisplayName("관리자 이의제기 판정 API는 판정 요청을 서비스로 전달한다")
    void judgeDispute_shouldDelegateRequest() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/disputes/10/judge")
                        .with(authentication(adminAuthentication(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACCEPTED\",\"comment\":\"accepted\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(adminDisputeService).judgeDispute(eq(1L), eq(10L), any(AdminJudgeDisputeRequestDto.class));
    }

    @Test
    @DisplayName("관리자 이의제기 상태 강제 변경 API는 변경 요청을 서비스로 전달한다")
    void overrideDisputeStatus_shouldDelegateRequest() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/disputes/10/override")
                        .with(authentication(adminAuthentication(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\",\"comment\":\"override\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(adminDisputeService).overrideDisputeStatus(eq(1L), eq(10L), any(AdminOverrideDisputeStatusRequestDto.class));
    }
}
