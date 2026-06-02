package com.example.team3final.domain.admin.auth.controller;

import com.example.team3final.domain.admin.auth.dto.request.AdminLoginRequestDto;
import com.example.team3final.domain.admin.auth.dto.response.AdminLoginResponseDto;
import com.example.team3final.domain.admin.auth.service.AdminAuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminAuthService adminAuthService;

    @Test
    @DisplayName("관리자 로그인 - 성공")
    void login_Success() throws Exception {
        // given
        AdminLoginRequestDto request = new AdminLoginRequestDto();
        ReflectionTestUtils.setField(request, "email", "admin@test.com");
        ReflectionTestUtils.setField(request, "password", "password");
        
        AdminLoginResponseDto responseDto = new AdminLoginResponseDto(1L, "Admin", null, "token");
        given(adminAuthService.login(any(AdminLoginRequestDto.class))).willReturn(responseDto);

        // when & then
        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.adminAccessToken").value("token"));
    }
}

