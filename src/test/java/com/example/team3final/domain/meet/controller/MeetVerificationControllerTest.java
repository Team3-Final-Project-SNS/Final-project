package com.example.team3final.domain.meet.controller;

import com.example.team3final.domain.meet.dto.response.CreateMeetExtensionResponseDto;
import com.example.team3final.domain.meet.dto.response.GetMeetExtensionResponseDto;
import com.example.team3final.domain.meet.entity.MeetVerification;
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
import org.springframework.test.util.ReflectionTestUtils;
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

@WebMvcTest(controllers = MeetVerificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class MeetVerificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MeetVerificationService meetVerificationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("API test")
    @WithMockCustomUser
    void createMeetExtension_ApiTest() throws Exception {
        // given
        Long matchId = 1L;

        MeetVerification meetVerification = MeetVerification.createPending(matchId);
        ReflectionTestUtils.setField(meetVerification, "extensionRequestedAt", LocalDateTime.now());

        CreateMeetExtensionResponseDto responseDto = CreateMeetExtensionResponseDto.of(
                meetVerification,
                "requester",
                LocalDateTime.now()
        );

        given(meetVerificationService.createMeetExtension(anyLong(), anyLong())).willReturn(responseDto);

        // when & then
        mockMvc.perform(post("/api/v1/matches/{matchId}/extension/request", matchId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("API test")
    @WithMockCustomUser
    void createPlaceVerification_ApiTest() throws Exception {
        // given
        given(meetVerificationService.createPlaceVerification(anyLong(), anyLong(), any())).willReturn(null);

        // when & then
        mockMvc.perform(post("/api/v1/matches/{matchId}/place-verification", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentLat\":37.0,\"currentLng\":127.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("API test")
    @WithMockCustomUser
    void getMeetQrByPost_ApiTest() throws Exception {
        // given
        given(meetVerificationService.getMeetQrByPost(anyLong(), anyLong())).willReturn(null);

        // when & then
        mockMvc.perform(get("/api/v1/posts/{postId}/qr", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("API test")
    @WithMockCustomUser
    void createQrScan_ApiTest() throws Exception {
        // given
        given(meetVerificationService.createQrScan(anyLong(), anyLong(), any())).willReturn(null);

        // when & then
        mockMvc.perform(post("/api/v1/matches/{matchId}/qr/scan", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qrToken\":\"token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("API test")
    @WithMockCustomUser
    void getMeetVerification_ApiTest() throws Exception {
        // given
        given(meetVerificationService.getMeetVerification(anyLong(), anyLong())).willReturn(null);

        // when & then
        mockMvc.perform(get("/api/v1/matches/{matchId}/verification", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("API test")
    @WithMockCustomUser
    void getMeetExtension_ApiTest() throws Exception {
        // given
        Long matchId = 1L;

        MeetVerification meetVerification = MeetVerification.createPending(matchId);
        ReflectionTestUtils.setField(meetVerification, "extensionRequestedAt", LocalDateTime.now());

        GetMeetExtensionResponseDto responseDto = GetMeetExtensionResponseDto.of(
                meetVerification,
                "requester",
                LocalDateTime.now(),
                1L
        );

        given(meetVerificationService.getMeetExtension(anyLong(), anyLong())).willReturn(responseDto);

        // when & then
        mockMvc.perform(get("/api/v1/matches/{matchId}/extension", matchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("API test")
    @WithMockCustomUser
    void acceptMeetExtension_ApiTest() throws Exception {
        // given
        given(meetVerificationService.acceptMeetExtension(anyLong(), anyLong())).willReturn(null);

        // when & then
        mockMvc.perform(patch("/api/v1/matches/{matchId}/extension/accept", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("API test")
    @WithMockCustomUser
    void rejectMeetExtension_ApiTest() throws Exception {
        // given
        given(meetVerificationService.rejectMeetExtension(anyLong(), anyLong())).willReturn(null);

        // when & then
        mockMvc.perform(patch("/api/v1/matches/{matchId}/extension/reject", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }
}
