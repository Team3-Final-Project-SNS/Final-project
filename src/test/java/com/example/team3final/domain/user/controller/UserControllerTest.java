package com.example.team3final.domain.user.controller;

import com.example.team3final.domain.auth.service.AuthService;
import com.example.team3final.domain.user.dto.request.UpdateUserRequestDto;
import com.example.team3final.domain.user.dto.request.WithdrawRequestDto;
import com.example.team3final.domain.user.dto.response.GetUserResponseDto;
import com.example.team3final.domain.user.enums.Gender;
import com.example.team3final.domain.user.enums.UserStatus;
import com.example.team3final.domain.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import com.example.team3final.test.security.WithMockCustomUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AuthService authService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("API test")
    @WithMockCustomUser
    void getUser_ApiTest() throws Exception {
        // given
        GetUserResponseDto response = new GetUserResponseDto(
                1L, "test@univ.ac.kr", "name", "nickname", 1L, "major", "123456", LocalDate.now(), Gender.MALE, 1000, new BigDecimal("36.5"), UserStatus.ACTIVE, LocalDateTime.now());
        given(userService.getUser(anyLong())).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("API test")
    @WithMockCustomUser
    void updateUser_ApiTest() throws Exception {
        // given
        UpdateUserRequestDto request = UpdateUserRequestDto.builder()
                .currentPassword("Password123")
                .newPassword("NewPassword123")
                .nickname("newNickname")
                .major("newMajor")
                .build();

        given(userService.updateUser(anyLong(), any())).willReturn(null);

        // when & then
        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("API test")
    @WithMockCustomUser
    void withdrawUser_ApiTest() throws Exception {
        // given
        WithdrawRequestDto request = new WithdrawRequestDto();
        ReflectionTestUtils.setField(request, "password", "Password123");

        given(authService.withdraw(anyLong(), any(), any(), any())).willReturn(null);

        // when & then
        mockMvc.perform(delete("/api/v1/users/me")
                        .cookie(new Cookie("refresh_token", "refresh-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }
}
