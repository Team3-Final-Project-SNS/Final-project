package com.example.team3final.domain.auth.controller;

import com.example.team3final.domain.auth.dto.request.OtpRequestDto;
import com.example.team3final.domain.auth.dto.request.OtpVerifyRequestDto;
import com.example.team3final.domain.auth.dto.request.SignupRequestDto;
import com.example.team3final.domain.auth.dto.request.LoginRequestDto;
import com.example.team3final.domain.auth.dto.response.OtpResponseDto;
import com.example.team3final.domain.auth.service.AuthService;
import com.example.team3final.domain.user.enums.Gender;
import com.example.team3final.test.security.WithMockCustomUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("API test")
    void sendEmailOtp_ApiTest() throws Exception {
        // given
        OtpRequestDto request = new OtpRequestDto("test@univ.ac.kr");
        OtpResponseDto response = new OtpResponseDto(300);

        given(authService.sendEmailOtp(any())).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/auth/email/otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("API test")
    void verifyEmailOtp_ApiTest() throws Exception {
        // given
        OtpVerifyRequestDto request = OtpVerifyRequestDto.builder()
                .email("test@univ.ac.kr")
                .otpCode("123456")
                .build();

        given(authService.verifyEmailOtp(any(), any())).willReturn(null);

        // when & then
        mockMvc.perform(post("/api/v1/auth/email/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("API test")
    void signup_ApiTest() throws Exception {
        // given
        SignupRequestDto request = SignupRequestDto.builder()
                .password("Password123")
                .name("name")
                .nickname("nickname")
                .major("major")
                .studentNumber("20")
                .birthDate(LocalDate.of(2000, 1, 1))
                .gender(Gender.MALE)
                .termAgreements(List.of())
                .build();

        given(authService.signup(any(), any(), any())).willReturn(null);

        // when & then
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("API test")
    void login_ApiTest() throws Exception {
        // given
        LoginRequestDto request = LoginRequestDto.builder()
                .email("test@univ.ac.kr")
                .password("Password123")
                .build();

        given(authService.login(any(), any())).willReturn(null);

        // when & then
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("API test")
    void refresh_ApiTest() throws Exception {
        // given
        given(authService.refresh(anyString(), anyString(), any())).willReturn(null);

        // when & then
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", "refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("API test")
    @WithMockCustomUser
    void logout_ApiTest() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie("refresh_token", "refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }
}
