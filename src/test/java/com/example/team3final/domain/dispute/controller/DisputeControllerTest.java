package com.example.team3final.domain.dispute.controller;

import com.example.team3final.domain.dispute.dto.response.CreateDisputeResponseDto;
import com.example.team3final.domain.dispute.enums.DisputeStatus;
import com.example.team3final.domain.dispute.enums.DisputeType;
import com.example.team3final.domain.dispute.service.DisputeService;
import com.example.team3final.domain.meet.service.MeetVerificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import com.example.team3final.test.security.WithMockCustomUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DisputeController.class)
@AutoConfigureMockMvc(addFilters = false)
class DisputeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DisputeService disputeService;

    @MockitoBean
    private MeetVerificationService meetVerificationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("이의제기 생성 API - 201 반환")
    @WithMockCustomUser
    void createDispute_ApiTest() throws Exception {
        // given
        Long matchId = 1L;
        String requestJson = "{\"disputeType\":\"GPS_ERROR\", \"reason\":\"REASON\"}";

        CreateDisputeResponseDto response = new CreateDisputeResponseDto(
                1L, 1L, DisputeType.GPS_ERROR, DisputeStatus.SUBMITTED, LocalDateTime.now());

        given(disputeService.createDispute(anyLong(), anyLong(), any())).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/matches/{matchId}/disputes", matchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("내 이의제기 조회 API - 200 반환")
    @WithMockCustomUser
    void getDispute_ApiTest() throws Exception {
        // given
        given(disputeService.getDispute(anyLong(), anyLong())).willReturn(null);

        // when & then
        mockMvc.perform(get("/api/v1/matches/{matchId}/disputes/me", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("노쇼 매칭 목록 조회 API - 200 반환")
    @WithMockCustomUser
    void getNoShowMatches_ApiTest() throws Exception {
        // given
        given(meetVerificationService.getNoShowMatchesForUser(anyLong())).willReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/v1/matches/me/no-show"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("이의제기 재제출 API - 201 반환")
    @WithMockCustomUser
    void reCreateDispute_ApiTest() throws Exception {
        // given
        String requestJson = "{\"disputeType\":\"GPS_ERROR\", \"reason\":\"REASON\"}";
        given(disputeService.reCreateDispute(anyLong(), anyLong(), any())).willReturn(null);

        // when & then
        mockMvc.perform(post("/api/v1/matches/{matchId}/disputes/resubmit", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }
}
