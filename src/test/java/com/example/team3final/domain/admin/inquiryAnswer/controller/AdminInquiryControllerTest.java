package com.example.team3final.domain.admin.inquiryAnswer.controller;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.admin.inquiryAnswer.dto.request.AdminCreateInquiryRequestDto;
import com.example.team3final.domain.admin.inquiryAnswer.service.AdminInquiryAnswerService;
import com.example.team3final.domain.inquiry.enums.InquiryAnswerStatus;
import com.example.team3final.domain.inquiry.enums.InquiryType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("관리자 문의 답변 컨트롤러 통합 테스트")
class AdminInquiryControllerTest extends ControllerTestSupport {

    @Mock
    private AdminInquiryAnswerService adminInquiryAnswerService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvcFor(new AdminInquiryController(adminInquiryAnswerService));
    }

    @Test
    @DisplayName("관리자 문의 목록 조회 API는 상태와 유형 필터를 서비스로 전달한다")
    void getInquiries_shouldBindFiltersAndDelegate() throws Exception {
        when(adminInquiryAnswerService.getInquiries(eq(1L), eq(InquiryAnswerStatus.PENDING),
                eq(InquiryType.ACCOUNT), any(Pageable.class)))
                .thenReturn(new PageResponseDto<>(List.of(), 0, 20, 0, 0, false));

        mockMvc.perform(get("/api/v1/admin/inquiries")
                        .with(authentication(adminAuthentication(1L)))
                        .param("status", "PENDING")
                        .param("type", "ACCOUNT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(adminInquiryAnswerService).getInquiries(eq(1L), eq(InquiryAnswerStatus.PENDING),
                eq(InquiryType.ACCOUNT), any(Pageable.class));
    }

    @Test
    @DisplayName("관리자 문의 상세 조회 API는 관리자 ID와 문의 ID를 서비스로 전달한다")
    void getInquiry_shouldPassAdminIdAndInquiryId() throws Exception {
        mockMvc.perform(get("/api/v1/admin/inquiries/10")
                        .with(authentication(adminAuthentication(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(adminInquiryAnswerService).getInquiry(1L, 10L);
    }

    @Test
    @DisplayName("관리자 문의 답변 생성 API는 201 Created와 성공 응답을 반환한다")
    void createAnswer_shouldReturnCreatedAndDelegate() throws Exception {
        mockMvc.perform(post("/api/v1/admin/inquiries/10/answers")
                        .with(authentication(adminAuthentication(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"answer content\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        verify(adminInquiryAnswerService).createAnswer(eq(1L), eq(10L), any(AdminCreateInquiryRequestDto.class));
    }
}
