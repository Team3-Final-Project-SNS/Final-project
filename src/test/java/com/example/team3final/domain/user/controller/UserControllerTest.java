package com.example.team3final.domain.user.controller;

import com.example.team3final.domain.auth.service.AuthService;
import com.example.team3final.domain.user.dto.request.UpdateUserRequestDto;
import com.example.team3final.domain.user.dto.request.WithdrawRequestDto;
import com.example.team3final.domain.user.service.UserCommandService;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("사용자 컨트롤러 통합 테스트")
class UserControllerTest extends ControllerTestSupport {

    @Mock
    private UserCommandService userCommandService;

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvcFor(new UserController(userCommandService, authService));
    }

    @Test
    @DisplayName("내 정보 조회 API는 인증 사용자 ID를 서비스로 전달한다")
    void getUser_shouldDelegateAuthenticatedUserId() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .with(authentication(userAuthentication(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(userCommandService).getUser(1L);
    }

    @Test
    @DisplayName("내 정보 수정 API는 인증 사용자 ID와 수정 요청을 서비스로 전달한다")
    void updateUser_shouldDelegateRequest() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                        .with(authentication(userAuthentication(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "password123",
                                  "newPassword": "newpass123",
                                  "nickname": "newnick",
                                  "major": "computer"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(userCommandService).updateUser(eq(1L), any(UpdateUserRequestDto.class));
    }

    @Test
    @DisplayName("회원 탈퇴 API는 refresh_token 쿠키와 탈퇴 요청을 서비스로 전달한다")
    void withdrawUser_shouldExtractRefreshTokenAndDelegate() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me")
                        .with(authentication(userAuthentication(1L)))
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", "refresh"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(authService).withdraw(eq(1L), any(WithdrawRequestDto.class), eq("refresh"), any(HttpServletResponse.class));
    }
}
