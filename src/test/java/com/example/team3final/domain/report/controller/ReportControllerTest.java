package com.example.team3final.domain.report.controller;

import com.example.team3final.domain.report.dto.request.CreateReportRequestDto;
import com.example.team3final.domain.report.service.ReportCommandService;
import com.example.team3final.test.controller.ControllerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("신고 컨트롤러 통합 테스트")
class ReportControllerTest extends ControllerTestSupport {

    @Mock
    private ReportCommandService reportCommandService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvcFor(new ReportController(reportCommandService));
    }

    @Test
    @DisplayName("신고 생성 API는 신고자 ID와 요청 본문을 서비스로 전달하고 201을 반환한다")
    void createReport_shouldReturnCreatedAndDelegate() throws Exception {
        mockMvc.perform(post("/api/v1/reports")
                        .with(authentication(userAuthentication(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetId": 2,
                                  "reason": "OTHER",
                                  "detail": "no show"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        verify(reportCommandService).createReport(eq(1L), any(CreateReportRequestDto.class));
    }
}
