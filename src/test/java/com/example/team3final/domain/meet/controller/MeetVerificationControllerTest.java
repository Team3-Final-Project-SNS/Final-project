package com.example.team3final.domain.meet.controller;

import com.example.team3final.domain.meet.dto.request.PlaceVerificationRequestDto;
import com.example.team3final.domain.meet.dto.response.PlaceVerificationResponseDto;
import com.example.team3final.domain.meet.service.MeetVerificationServiceImpl;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MeetVerificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MeetVerificationServiceImpl meetVerificationService;

    @Test
    @DisplayName("장소 인증 요청 - 성공")
    void createPlaceVerification_Success() throws Exception {
        // given
        User user = User.builder().email("test@test.ac.kr").build();
        ReflectionTestUtils.setField(user, "id", 1L);
        UserDetailsImpl userDetails = new UserDetailsImpl(user);

        PlaceVerificationRequestDto request = new PlaceVerificationRequestDto();
        ReflectionTestUtils.setField(request, "currentLat", new BigDecimal("37.5"));
        ReflectionTestUtils.setField(request, "currentLng", new BigDecimal("127.0"));

        given(meetVerificationService.createPlaceVerification(anyLong(), anyLong(), any()))
                .willReturn(new PlaceVerificationResponseDto(
                        100L, null, 0, null, null, true
                ));

        // when & then
        mockMvc.perform(post("/api/v1/matches/100/place-verification")
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }
}

