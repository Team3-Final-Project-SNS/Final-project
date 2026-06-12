package com.example.team3final.domain.admin.inquiryAnswer.controller;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.enums.AdminRole;
import com.example.team3final.domain.admin.inquiryAnswer.dto.request.AdminCreateInquiryRequestDto;
import com.example.team3final.domain.admin.inquiryAnswer.dto.response.AdminCreateInquiryResponseDto;
import com.example.team3final.domain.admin.inquiryAnswer.dto.response.AdminGetInquiriesResponseDto;
import com.example.team3final.domain.admin.inquiryAnswer.dto.response.AdminGetInquiryResponseDto;
import com.example.team3final.domain.admin.security.AdminDetailsImpl;
import com.example.team3final.domain.admin.inquiryAnswer.service.AdminInquiryAnswerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminInquiryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminInquiryAnswerService adminInquiryAnswerService;

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
    @DisplayName("관리자 고객 문의 목록 조회 API test")
    void getInquiries_ApiTest() throws Exception {
        // given
        PageResponseDto<AdminGetInquiriesResponseDto> response = PageResponseDto.from(new PageImpl<>(List.of()));
        given(adminInquiryAnswerService.getInquiries(anyLong(), any(), any(), any())).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/admin/inquiries")
                        .with(user(createMockAdminDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("관리자 고객 문의 상세 조회 API test")
    void getInquiry_ApiTest() throws Exception {
        // given
        AdminGetInquiryResponseDto response = new AdminGetInquiryResponseDto(1L, "nickname", "email", "univ", "title", "content", null, null, null, null);
        given(adminInquiryAnswerService.getInquiry(anyLong(), anyLong())).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/admin/inquiries/1")
                        .with(user(createMockAdminDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("관리자 고객 문의 답변 생성 API test")
    void createAnswer_ApiTest() throws Exception {
        // given
        AdminCreateInquiryRequestDto request = new AdminCreateInquiryRequestDto("ANSWER");
        AdminCreateInquiryResponseDto response = new AdminCreateInquiryResponseDto(1L, 1L, "admin", "content", null);
        given(adminInquiryAnswerService.createAnswer(anyLong(), anyLong(), any())).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/admin/inquiries/1/answers")
                        .with(user(createMockAdminDetails()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }
}
