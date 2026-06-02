package com.example.team3final.domain.match.controller;

import com.example.team3final.domain.match.dto.response.CreateMatchResponseDto;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.service.MatchService;
import com.example.team3final.domain.meet.service.MeetVerificationServiceImpl;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.service.UserDetailsImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MatchService matchService;

    @MockitoBean
    private MeetVerificationServiceImpl meetVerificationService;

    @Test
    @DisplayName("매칭 신청 - 성공")
    void createMatch_Success() throws Exception {

        // ===== given =====

        User user = User.builder()
                .email("test@test.ac.kr")
                .password("encodedPassword")
                .name("Test User")
                .nickname("test-user")
                .universityId(1L)
                .major("컴퓨터공학과")
                .studentNumber("24")
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);

        UserDetailsImpl userDetails = new UserDetailsImpl(user);

        CreateMatchResponseDto responseDto = new CreateMatchResponseDto(
                200L,                // matchId
                100L,                // postId
                1L,                  // authorId
                "author",             // authorNickname
                2L,                  // applicantId
                "applicant",          // applicantNickname
                500,                 // authorDeposit
                500,                 // applicantDeposit
                MatchStatus.MATCHED, // status
                null,                // chatRoomId
                LocalDateTime.now()  // matchedAt
        );

        // matchService.createMatch() Stub
        given(matchService.createMatch(anyLong(), anyLong())).willReturn(responseDto);

        doNothing().when(meetVerificationService).createPendingVerification(anyLong());

        // ===== when & then =====
        mockMvc.perform(
                        post("/api/v1/posts/100/matches")
                                .with(user(userDetails))
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.matchId").value(200));
    }
}
