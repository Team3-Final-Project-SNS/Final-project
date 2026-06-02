package com.example.team3final.domain.admin.dispute.controller;

import com.example.team3final.domain.admin.dispute.dto.request.AdminJudgeDisputeRequestDto;
import com.example.team3final.domain.admin.dispute.dto.response.AdminJudgeDisputeResponseDto;
import com.example.team3final.domain.admin.dispute.service.AdminDisputeService;
import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.security.AdminDetailsImpl;
import com.example.team3final.domain.dispute.enums.DisputeStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminDisputeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminDisputeService adminDisputeService;

    @Test
    @DisplayName("이의제기 판정 - 성공")
    void judgeDispute_Success() throws Exception {
        // given
        Admin admin = Admin.builder().email("admin@test.com").password("pass").build();
        ReflectionTestUtils.setField(admin, "id", 1L);
        ReflectionTestUtils.setField(admin, "role", com.example.team3final.domain.admin.enums.AdminRole.SUPER_ADMIN);
        ReflectionTestUtils.setField(admin, "isActive", true);
        AdminDetailsImpl adminDetails = new AdminDetailsImpl(admin);

        AdminJudgeDisputeRequestDto request = new AdminJudgeDisputeRequestDto();
        ReflectionTestUtils.setField(request, "status", DisputeStatus.ACCEPTED);
        ReflectionTestUtils.setField(request, "comment", "Valid comment");

        AdminJudgeDisputeResponseDto responseDto = new AdminJudgeDisputeResponseDto(
                100L, 1L, DisputeStatus.ACCEPTED, "comment", 0, java.time.LocalDateTime.now()
        );
        given(adminDisputeService.judgeDispute(anyLong(), anyLong(), any())).willReturn(responseDto);

        // when & then
        mockMvc.perform(patch("/api/v1/admin/disputes/100/judge")
                        .with(SecurityMockMvcRequestPostProcessors.user(adminDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists());
    }
}

