package com.example.team3final.domain.admin.report.controller;

import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.report.dto.request.AdminProcessReportRequestDto;
import com.example.team3final.domain.admin.report.dto.response.AdminProcessReportResponseDto;
import com.example.team3final.domain.admin.report.service.AdminReportService;
import com.example.team3final.domain.admin.security.AdminDetailsImpl;
import com.example.team3final.domain.report.enums.ReportStatus;
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

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminReportService adminReportService;

    @Test
    @DisplayName("신고 처리 - 성공")
    void processReport_Success() throws Exception {
        // given
        Admin admin = Admin.builder().email("admin@test.com").password("pass").build();
        ReflectionTestUtils.setField(admin, "id", 1L);
        ReflectionTestUtils.setField(admin, "role", com.example.team3final.domain.admin.enums.AdminRole.SUPER_ADMIN);
        ReflectionTestUtils.setField(admin, "isActive", true);
        AdminDetailsImpl adminDetails = new AdminDetailsImpl(admin);

        AdminProcessReportRequestDto request = new AdminProcessReportRequestDto();
        ReflectionTestUtils.setField(request, "reportStatus", ReportStatus.ACCEPTED);

        AdminProcessReportResponseDto responseDto = new AdminProcessReportResponseDto(
                100L, ReportStatus.ACCEPTED, true, 50, LocalDateTime.now()
        );
        given(adminReportService.processReport(anyLong(), anyLong(), any())).willReturn(responseDto);

        // when & then
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(adminDetails, null, adminDetails.getAuthorities())
        );
        mockMvc.perform(patch("/api/v1/admin/reports/100/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reportId").value(100));
    }
}

