package com.example.team3final.domain.admin.dispute.controller;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.admin.dispute.dto.request.AdminJudgeDisputeRequestDto;
import com.example.team3final.domain.admin.dispute.dto.request.AdminOverrideDisputeStatusRequestDto;
import com.example.team3final.domain.admin.dispute.dto.response.AdminJudgeDisputeResponseDto;
import com.example.team3final.domain.admin.dispute.dto.response.GetAdminDisputeResponseDto;
import com.example.team3final.domain.admin.dispute.dto.response.GetAdminDisputesResponseDto;
import com.example.team3final.domain.admin.dispute.service.AdminDisputeService;
import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.enums.AdminRole;
import com.example.team3final.domain.admin.security.AdminDetailsImpl;
import com.example.team3final.domain.dispute.enums.DisputeStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminDisputeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminDisputeService adminDisputeService;

    @Autowired
    private ObjectMapper objectMapper;

    private AdminDetailsImpl createMockAdminDetails() {
        Admin admin = Admin.builder()
                .email("admin@test.com")
                .role(AdminRole.SUPER_ADMIN)
                .build();
        ReflectionTestUtils.setField(admin, "id", 1L);
        return new AdminDetailsImpl(admin);
    }

    @Test
    @DisplayName("API test")
    void getDisputes_ApiTest() throws Exception {
        // given
        PageResponseDto<GetAdminDisputesResponseDto> response = PageResponseDto.from(new PageImpl<>(List.of()));
        given(adminDisputeService.getDisputes(anyLong(), any(), any())).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/admin/disputes")
                        .with(user(createMockAdminDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("API test")
    void getDispute_ApiTest() throws Exception {
        // given
        GetAdminDisputeResponseDto response = new GetAdminDisputeResponseDto(1L, 1L, "nickname", null, "reason", null, null, null, null, null, List.of());
        given(adminDisputeService.getDispute(anyLong(), anyLong())).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/admin/disputes/1")
                        .with(user(createMockAdminDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("API test")
    void judgeDispute_ApiTest() throws Exception {
        // given
        AdminJudgeDisputeRequestDto request = new AdminJudgeDisputeRequestDto();
        ReflectionTestUtils.setField(request, "status", DisputeStatus.ACCEPTED);
        ReflectionTestUtils.setField(request, "comment", "정상 판정");

        AdminJudgeDisputeResponseDto response =
                new AdminJudgeDisputeResponseDto(1L, 1L, DisputeStatus.ACCEPTED, "comment", 0, null);

        given(adminDisputeService.judgeDispute(anyLong(), anyLong(), any())).willReturn(response);

        // when & then
        mockMvc.perform(patch("/api/v1/admin/disputes/1/judge")
                        .with(user(createMockAdminDetails()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("API test")
    void overrideDisputeStatus_ApiTest() throws Exception {
        // given
        AdminOverrideDisputeStatusRequestDto request = new AdminOverrideDisputeStatusRequestDto();
        ReflectionTestUtils.setField(request, "status", DisputeStatus.ACCEPTED);
        ReflectionTestUtils.setField(request, "comment", "관리자 상태 변경");

        AdminJudgeDisputeResponseDto response =
                new AdminJudgeDisputeResponseDto(1L, 1L, DisputeStatus.ACCEPTED, "comment", 0, null);

        given(adminDisputeService.overrideDisputeStatus(anyLong(), anyLong(), any())).willReturn(response);

        // when & then
        mockMvc.perform(patch("/api/v1/admin/disputes/1/override")
                        .with(user(createMockAdminDetails()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }
}
