package com.example.team3final.domain.admin.auth.controller;

import com.example.team3final.domain.admin.auth.dto.request.AdminLoginRequestDto;
import com.example.team3final.domain.admin.auth.service.AdminAuthService;
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
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("관리자 인증 컨트롤러 통합 테스트")
class AdminAuthControllerTest extends ControllerTestSupport {

    @Mock
    private AdminAuthService adminAuthService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvcFor(new AdminAuthController(adminAuthService));
    }

    @Test
    @DisplayName("관리자 로그인 API는 요청 본문을 서비스로 전달하고 성공 응답을 반환한다")
    void login_shouldDelegateToService() throws Exception {
        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "admin@test.com",
                                  "password": "password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(adminAuthService).login(any(AdminLoginRequestDto.class));
    }
}
