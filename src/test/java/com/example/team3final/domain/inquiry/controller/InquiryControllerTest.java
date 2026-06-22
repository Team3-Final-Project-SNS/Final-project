package com.example.team3final.domain.inquiry.controller;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.inquiry.dto.request.CreateInquiryRequestDto;
import com.example.team3final.domain.inquiry.dto.response.CancelInquiryResponseDto;
import com.example.team3final.domain.inquiry.dto.response.CreateInquiryResponseDto;
import com.example.team3final.domain.inquiry.dto.response.GetAllInquiriesResponseDto;
import com.example.team3final.domain.inquiry.dto.response.GetOneInquiryResponseDto;
import com.example.team3final.domain.inquiry.enums.InquiryAnswerStatus;
import com.example.team3final.domain.inquiry.enums.InquiryType;
import com.example.team3final.domain.inquiry.service.InquiryCommandService;
import com.example.team3final.domain.inquiry.service.InquiryQueryService;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("문의 컨트롤러 통합 테스트")
class InquiryControllerTest extends ControllerTestSupport {

    @Mock
    private InquiryCommandService inquiryCommandService;

    @Mock
    private InquiryQueryService inquiryQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvcFor(new InquiryController(inquiryCommandService, inquiryQueryService));
    }

    @Test
    @DisplayName("문의 접수 API는 사용자 ID와 요청 본문을 서비스로 전달하고 201을 반환한다")
    void createInquiry_shouldReturnCreatedAndDelegate() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 12, 0);
        when(inquiryCommandService.createInquiry(eq(1L), any(CreateInquiryRequestDto.class)))
                .thenReturn(new CreateInquiryResponseDto(10L, InquiryAnswerStatus.PENDING, createdAt));

        mockMvc.perform(post("/api/v1/inquiries")
                        .with(authentication(userAuthentication(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "결제 문의",
                                  "content": "결제 내역을 확인하고 싶습니다.",
                                  "type": "PAYMENT"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.inquiryId").value(10));

        verify(inquiryCommandService).createInquiry(eq(1L), any(CreateInquiryRequestDto.class));
    }

    @Test
    @DisplayName("문의 상세 조회 API는 사용자 ID와 문의 ID로 상세 정보를 조회한다")
    void getOneInquiry_shouldReturnInquiry() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 12, 0);
        when(inquiryQueryService.getOneInquiry(1L, 10L))
                .thenReturn(new GetOneInquiryResponseDto(
                        10L,
                        "결제 문의",
                        "결제 내역을 확인하고 싶습니다.",
                        InquiryType.PAYMENT,
                        InquiryAnswerStatus.PENDING,
                        null,
                        createdAt));

        mockMvc.perform(get("/api/v1/inquiries/{inquiryId}", 10L)
                        .with(authentication(userAuthentication(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.inquiryId").value(10));

        verify(inquiryQueryService).getOneInquiry(1L, 10L);
    }

    @Test
    @DisplayName("내 문의 목록 조회 API는 페이지 조건과 사용자 ID로 문의 목록을 조회한다")
    void getAllInquiries_shouldReturnPagedInquiries() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 12, 0);
        PageResponseDto<GetAllInquiriesResponseDto> response = new PageResponseDto<>(
                List.of(new GetAllInquiriesResponseDto(10L, "결제 문의", InquiryType.PAYMENT, InquiryAnswerStatus.PENDING, createdAt)),
                0,
                10,
                1,
                1,
                false);
        when(inquiryQueryService.getAllInquiries(eq(1L), any(Pageable.class))).thenReturn(response);

        mockMvc.perform(get("/api/v1/inquiries/me")
                        .with(authentication(userAuthentication(1L)))
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].inquiryId").value(10));

        verify(inquiryQueryService).getAllInquiries(eq(1L), any(Pageable.class));
    }

    @Test
    @DisplayName("문의 취소 API는 사용자 ID와 문의 ID를 서비스로 전달한다")
    void cancelInquiry_shouldReturnCancelledInquiry() throws Exception {
        LocalDateTime cancelledAt = LocalDateTime.of(2026, 1, 1, 12, 0);
        when(inquiryCommandService.cancelInquiry(1L, 10L))
                .thenReturn(new CancelInquiryResponseDto(10L, cancelledAt));

        mockMvc.perform(patch("/api/v1/inquiries/{inquiryId}/cancel", 10L)
                        .with(authentication(userAuthentication(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.inquiryId").value(10));

        verify(inquiryCommandService).cancelInquiry(1L, 10L);
    }
}
