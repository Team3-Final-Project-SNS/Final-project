package com.example.team3final.domain.admin.report.controller;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.admin.report.dto.request.AdminProcessReportRequestDto;
import com.example.team3final.domain.admin.report.service.AdminReportService;
import com.example.team3final.domain.report.enums.ReportStatus;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("관리자 신고 컨트롤러 통합 테스트")
class AdminReportControllerTest extends ControllerTestSupport {

    @Mock
    private AdminReportService adminReportService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvcFor(new AdminReportController(adminReportService));
    }

    @Test
    @DisplayName("관리자 신고 목록 조회 API는 처리 상태와 페이징 조건을 서비스로 전달한다")
    void getReports_shouldBindStatusAndPageable() throws Exception {
        when(adminReportService.getReports(eq(1L), eq(ReportStatus.PENDING), any(Pageable.class)))
                .thenReturn(new PageResponseDto<>(List.of(), 0, 20, 0, 0, false));

        mockMvc.perform(get("/api/v1/admin/reports")
                        .with(authentication(adminAuthentication(1L)))
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(adminReportService).getReports(eq(1L), eq(ReportStatus.PENDING), any(Pageable.class));
    }

    @Test
    @DisplayName("관리자 신고 상세 조회 API는 관리자 ID와 신고 ID를 서비스로 전달한다")
    void getReport_shouldPassAdminIdAndReportId() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports/10")
                        .with(authentication(adminAuthentication(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(adminReportService).getReport(1L, 10L);
    }

    @Test
    @DisplayName("관리자 신고 처리 API는 처리 요청 본문을 서비스로 전달한다")
    void processReport_shouldPassAdminIdReportIdAndRequest() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/reports/10/process")
                        .with(authentication(adminAuthentication(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportStatus\":\"ACCEPTED\",\"comment\":\"confirmed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(adminReportService).processReport(eq(1L), eq(10L), any(AdminProcessReportRequestDto.class));
    }
}
