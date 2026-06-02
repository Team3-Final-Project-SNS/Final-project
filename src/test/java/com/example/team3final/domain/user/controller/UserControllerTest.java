package com.example.team3final.domain.user.controller;

import com.example.team3final.domain.auth.service.AuthService;
import com.example.team3final.domain.user.dto.response.GetUserResponseDto;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.enums.Gender;
import com.example.team3final.domain.user.service.UserDetailsImpl;
import com.example.team3final.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("내 정보 조회 - 성공")
    void getUser_Success() throws Exception {
        // given
        User user = User.builder()
                .email("test@test.ac.kr")
                .name("Name")
                .nickname("Nick")
                .universityId(1L)
                .major("Major")
                .studentNumber("2024")
                .birthDate(LocalDate.now())
                .gender(Gender.MALE)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        UserDetailsImpl userDetails = new UserDetailsImpl(user);

        GetUserResponseDto responseDto = GetUserResponseDto.of(user);
        given(userService.getUser(anyLong())).willReturn(responseDto);

        // when & then
        mockMvc.perform(get("/api/v1/users/me")
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nickname").value("Nick"));
    }
}

