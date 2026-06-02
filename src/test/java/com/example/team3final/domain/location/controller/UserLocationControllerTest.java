package com.example.team3final.domain.location.controller;

import com.example.team3final.domain.location.dto.request.UpdateLocationRequestDto;
import com.example.team3final.domain.location.service.UserLocationService;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.service.UserDetailsImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserLocationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserLocationService userLocationService;

    @Test
    @DisplayName("내 위치 업데이트 - 성공")
    void updateMyLocation_Success() throws Exception {
        // given
        User user = User.builder().email("test@test.ac.kr").build();
        ReflectionTestUtils.setField(user, "id", 1L);
        UserDetailsImpl userDetails = new UserDetailsImpl(user);

        UpdateLocationRequestDto request = UpdateLocationRequestDto.builder()
                .latitude(new BigDecimal("37.5"))
                .longitude(new BigDecimal("127.0"))
                .build();

        // when & then
        mockMvc.perform(put("/api/v1/matches/100/location")
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}

