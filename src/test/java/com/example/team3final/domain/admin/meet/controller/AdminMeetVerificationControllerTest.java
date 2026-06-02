package com.example.team3final.domain.admin.meet.controller;

import com.example.team3final.domain.admin.meet.dto.response.AdminNoShowCandidateResponseDto;
import com.example.team3final.domain.admin.meet.service.AdminMeetVerificationService;
import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.security.AdminDetailsImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminMeetVerificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminMeetVerificationService adminMeetVerificationService;

    @Test
    @DisplayName("노쇼 후보군 조회 - 성공")
    void getNoShowCandidates_Success() throws Exception {
        // given
        Admin admin = Admin.builder().email("admin@test.com").password("pass").build();
        ReflectionTestUtils.setField(admin, "id", 1L);
        ReflectionTestUtils.setField(admin, "role", com.example.team3final.domain.admin.enums.AdminRole.SUPER_ADMIN);
        ReflectionTestUtils.setField(admin, "isActive", true);
        AdminDetailsImpl adminDetails = new AdminDetailsImpl(admin);

        PageResponseDto<AdminNoShowCandidateResponseDto> responseDto = PageResponseDto.from(new PageImpl<>(List.of()));
        given(adminMeetVerificationService.getNoShowCandidates(any())).willReturn(responseDto);

        // when & then
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(adminDetails, null, adminDetails.getAuthorities())
        );
        mockMvc.perform(get("/api/v1/admin/no-show-candidates")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}

