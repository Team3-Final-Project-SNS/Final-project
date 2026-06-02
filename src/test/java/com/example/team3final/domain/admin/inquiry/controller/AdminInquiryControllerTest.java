package com.example.team3final.domain.admin.inquiry.controller;

import com.example.team3final.domain.admin.inquiry.dto.response.AdminGetInquiryResponseDto;
import com.example.team3final.domain.admin.inquiry.service.AdminInquiryService;
import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.security.AdminDetailsImpl;
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

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminInquiryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminInquiryService adminInquiryService;

    @Test
    @DisplayName("문의 상세 조회 - 성공")
    void getInquiry_Success() throws Exception {
        // given
        Admin admin = Admin.builder().email("admin@test.com").password("pass").build();
        ReflectionTestUtils.setField(admin, "id", 1L);
        ReflectionTestUtils.setField(admin, "role", com.example.team3final.domain.admin.enums.AdminRole.SUPER_ADMIN);
        ReflectionTestUtils.setField(admin, "isActive", true);
        AdminDetailsImpl adminDetails = new AdminDetailsImpl(admin);

        // Stubbing getInquiry
        AdminGetInquiryResponseDto responseDto = new AdminGetInquiryResponseDto(
                100L, "User", "email@test.com", "Univ", "Title", "Content", 
                com.example.team3final.domain.inquiry.enums.InquiryType.USAGE, 
                com.example.team3final.domain.inquiry.enums.InquiryAnswerStatus.PENDING, 
                null, java.time.LocalDateTime.now(), java.time.LocalDateTime.now()
        );
        given(adminInquiryService.getInquiry(anyLong(), anyLong())).willReturn(responseDto);

        // when & then
        mockMvc.perform(get("/api/v1/admin/inquiries/100")
                        .with(SecurityMockMvcRequestPostProcessors.user(adminDetails))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.inquiryId").value(100));
    }
}

