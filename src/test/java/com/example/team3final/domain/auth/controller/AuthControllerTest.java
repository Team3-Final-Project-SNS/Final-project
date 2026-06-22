package com.example.team3final.domain.auth.controller;

import com.example.team3final.domain.auth.dto.request.*;
import com.example.team3final.domain.auth.service.AuthService;
import com.example.team3final.test.controller.ControllerTestSupport;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("인증 컨트롤러 통합 테스트")
class AuthControllerTest extends ControllerTestSupport {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvcFor(new AuthController(authService));
    }

    @Test
    @DisplayName("OTP 발송 API는 이메일 요청을 서비스로 전달한다")
    void sendEmailOtp_shouldDelegateRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/email/otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@test.ac.kr\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(authService).sendEmailOtp(any(OtpRequestDto.class));
    }

    @Test
    @DisplayName("OTP 검증 API는 검증 요청과 응답 객체를 서비스로 전달한다")
    void verifyEmailOtp_shouldDelegateRequestAndResponse() throws Exception {
        mockMvc.perform(post("/api/v1/auth/email/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@test.ac.kr\",\"otpCode\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(authService).verifyEmailOtp(any(OtpVerifyRequestDto.class), any(HttpServletResponse.class));
    }

    @Test
    @DisplayName("회원가입 API는 요청 본문과 HTTP 요청/응답 객체를 서비스로 전달하고 201을 반환한다")
    void signup_shouldReturnCreatedAndDelegate() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "password": "password123",
                                  "name": "tester",
                                  "nickname": "tester",
                                  "major": "computer",
                                  "studentNumber": "24",
                                  "birthDate": "2000-01-01",
                                  "gender": "MALE",
                                  "termAgreements": [
                                    {"termVersion": "service-v1", "agreed": true}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        verify(authService).signup(any(SignupRequestDto.class), any(), any(HttpServletResponse.class));
    }

    @Test
    @DisplayName("로그인 API는 로그인 요청과 응답 객체를 서비스로 전달한다")
    void login_shouldDelegateRequestAndResponse() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@test.ac.kr\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(authService).login(any(LoginRequestDto.class), any(HttpServletResponse.class));
    }

    @Test
    @DisplayName("토큰 재발급 API는 refresh_token과 device_id 쿠키 값을 서비스로 전달한다")
    void refresh_shouldExtractCookiesAndDelegate() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", "refresh"))
                        .cookie(new jakarta.servlet.http.Cookie("device_id", "device")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(authService).refresh(eq("refresh"), eq("device"), any(HttpServletResponse.class));
    }

    @Test
    @DisplayName("로그아웃 API는 refresh_token과 device_id 쿠키 값을 서비스로 전달한다")
    void logout_shouldExtractCookiesAndDelegate() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", "refresh"))
                        .cookie(new jakarta.servlet.http.Cookie("device_id", "device")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(authService).logout(eq("refresh"), eq("device"), any(HttpServletResponse.class));
    }
}
