package com.example.team3final.domain.match.controller;

import com.example.team3final.domain.match.dto.response.CreateMatchResponseDto;
import com.example.team3final.domain.match.dto.request.CancelMatchRequestDto;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.service.MatchService;
import com.example.team3final.domain.meet.service.MeetVerificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.example.team3final.test.security.WithMockCustomUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    private MeetVerificationService meetVerificationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("API test")
    @WithMockCustomUser
    void createMatch_ApiTest() throws Exception {
        // given
        Long postId = 1L;
        CreateMatchResponseDto response = new CreateMatchResponseDto(
                100L, postId, 2L, "author", 3L, "applicant", 1000, 1000, MatchStatus.MATCHED, 10L, LocalDateTime.now());

        given(matchService.createMatch(anyLong(), anyLong())).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/posts/{postId}/matches", postId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("API test")
    @WithMockCustomUser
    void getMatch_ApiTest() throws Exception {
        // given
        given(matchService.getMatch(anyLong(), anyLong())).willReturn(null);

        // when & then
        mockMvc.perform(get("/api/v1/matches/{matchId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("API test")
    @WithMockCustomUser
    void getMatches_ApiTest() throws Exception {
        // given
        given(matchService.getMatches(anyLong(), any(), any())).willReturn(null);

        // when & then
        mockMvc.perform(get("/api/v1/matches/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("API test")
    @WithMockCustomUser
    void cancelMatch_ApiTest() throws Exception {
        // given
        CancelMatchRequestDto request = new CancelMatchRequestDto("change of plan");
        given(matchService.cancelMatch(anyLong(), anyLong(), any())).willReturn(null);

        // when & then
        mockMvc.perform(patch("/api/v1/matches/{matchId}/cancel", 1L)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }
}
