package com.example.team3final.domain.report.controller;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.ReportException;
import com.example.team3final.domain.report.dto.request.CreateReportRequestDto;
import com.example.team3final.domain.report.dto.response.CreateReportResponseDto;
import com.example.team3final.domain.report.enums.ReportReason;
import com.example.team3final.domain.report.service.ReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import com.example.team3final.test.security.WithMockCustomUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReportController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("API test")
    @WithMockCustomUser
    void createReport_ApiTest() throws Exception {
        // given
        CreateReportRequestDto request = new CreateReportRequestDto(100L, ReportReason.ABUSE, "DETAIL");
        CreateReportResponseDto response = new CreateReportResponseDto(1L, 100L, "PENDING", LocalDateTime.now());

        given(reportService.createReport(anyLong(), any())).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("신고 생성 API - 신고 대상 ID가 없으면 400")
    @WithMockCustomUser
    void createReport_TargetIdRequired() throws Exception {
        CreateReportRequestDto request = new CreateReportRequestDto(null, ReportReason.ABUSE, "DETAIL");

        mockMvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("Validation Failed"));
    }

    @Test
    @DisplayName("신고 생성 API - 신고 사유가 없으면 400")
    @WithMockCustomUser
    void createReport_ReasonRequired() throws Exception {
        CreateReportRequestDto request = new CreateReportRequestDto(100L, null, "DETAIL");

        mockMvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("Validation Failed"));
    }

    @Test
    @DisplayName("신고 생성 API - 상세 내용이 500자를 넘으면 400")
    @WithMockCustomUser
    void createReport_DetailTooLong() throws Exception {
        CreateReportRequestDto request =
                new CreateReportRequestDto(100L, ReportReason.ABUSE, "a".repeat(501));

        mockMvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("상세 내용은 500자 이하여야 합니다."));
    }

    @Test
    @DisplayName("신고 생성 API - 중복 신고 예외를 409로 반환한다")
    @WithMockCustomUser
    void createReport_AlreadyReported() throws Exception {
        CreateReportRequestDto request = new CreateReportRequestDto(100L, ReportReason.ABUSE, "DETAIL");
        given(reportService.createReport(anyLong(), any()))
                .willThrow(new ReportException(ErrorCode.REPORT_ALREADY_REPORTED));

        mockMvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_005"));
    }
}
