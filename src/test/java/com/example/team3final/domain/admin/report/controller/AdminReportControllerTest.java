package com.example.team3final.domain.admin.report.controller;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.admin.report.dto.request.AdminProcessReportRequestDto;
import com.example.team3final.domain.admin.report.dto.response.AdminGetReportsResponseDto;
import com.example.team3final.domain.admin.report.service.AdminReportService;
import com.example.team3final.domain.report.enums.ReportStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.example.team3final.test.security.WithMockAdmin;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminReportService adminReportService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("API test")
    @WithMockAdmin
    void getReports_ApiTest() throws Exception {
        // given
        PageResponseDto<AdminGetReportsResponseDto> response = PageResponseDto.from(new PageImpl<>(List.of()));
        given(adminReportService.getReports(anyLong(), any(), any())).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/admin/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("API test")
    @WithMockAdmin
    void processReport_ApiTest() throws Exception {
        // given
        AdminProcessReportRequestDto request = new AdminProcessReportRequestDto();
        ReflectionTestUtils.setField(request, "reportStatus", ReportStatus.ACCEPTED);

        given(adminReportService.processReport(anyLong(), anyLong(), any()))
                .willReturn(null);

        // when & then
        mockMvc.perform(patch("/api/v1/admin/reports/{reportId}/process", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("API test")
    @WithMockAdmin
    void getReport_ApiTest() throws Exception {
        // given
        given(adminReportService.getReport(anyLong(), anyLong())).willReturn(null);

        // when & then
        mockMvc.perform(get("/api/v1/admin/reports/{reportId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }
}
