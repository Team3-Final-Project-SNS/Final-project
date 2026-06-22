package com.example.team3final.domain.meet.controller;

import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.meet.dto.request.PlaceVerificationRequestDto;
import com.example.team3final.domain.meet.dto.request.QrScanRequestDto;
import com.example.team3final.domain.meet.dto.response.AcceptMeetExtensionResponseDto;
import com.example.team3final.domain.meet.dto.response.CreateMeetExtensionResponseDto;
import com.example.team3final.domain.meet.dto.response.GetMeetExtensionResponseDto;
import com.example.team3final.domain.meet.dto.response.MeetVerificationResponseDto;
import com.example.team3final.domain.meet.dto.response.PlaceVerificationResponseDto;
import com.example.team3final.domain.meet.dto.response.QrResponseDto;
import com.example.team3final.domain.meet.dto.response.QrScanResponseDto;
import com.example.team3final.domain.meet.dto.response.RejectMeetExtensionResponseDto;
import com.example.team3final.domain.meet.enums.ExtensionStatus;
import com.example.team3final.domain.meet.enums.VerificationStatus;
import com.example.team3final.domain.meet.service.MeetVerificationCommandService;
import com.example.team3final.domain.meet.service.MeetVerificationQueryService;
import com.example.team3final.test.controller.ControllerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("만남 인증 컨트롤러 통합 테스트")
class MeetVerificationControllerTest extends ControllerTestSupport {

    @Mock
    private MeetVerificationCommandService meetVerificationCommandService;

    @Mock
    private MeetVerificationQueryService meetVerificationQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvcFor(new MeetVerificationController(meetVerificationCommandService, meetVerificationQueryService));
    }

    @Test
    @DisplayName("GPS 장소 인증 API는 사용자 ID와 매치 ID, 현재 위치를 서비스로 전달한다")
    void createPlaceVerification_shouldReturnVerificationResult() throws Exception {
        LocalDateTime verifiedAt = LocalDateTime.of(2026, 1, 1, 12, 0);
        when(meetVerificationCommandService.createPlaceVerification(eq(1L), eq(10L), any(PlaceVerificationRequestDto.class)))
                .thenReturn(new PlaceVerificationResponseDto(10L, VerificationStatus.VERIFIED, 10.5, verifiedAt, verifiedAt, true));

        mockMvc.perform(post("/api/v1/matches/{matchId}/place-verification", 10L)
                        .with(authentication(userAuthentication(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentLat": 37.5665,
                                  "currentLng": 126.9780
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.matchId").value(10));

        verify(meetVerificationCommandService).createPlaceVerification(eq(1L), eq(10L), any(PlaceVerificationRequestDto.class));
    }

    @Test
    @DisplayName("게시글 QR 조회 API는 사용자 ID와 게시글 ID로 QR 정보를 조회한다")
    void getMeetQrByPost_shouldReturnQr() throws Exception {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 1, 1, 12, 30);
        when(meetVerificationQueryService.getMeetQrByPost(1L, 20L))
                .thenReturn(new QrResponseDto(20L, "qr-token", expiresAt));

        mockMvc.perform(get("/api/v1/posts/{postId}/qr", 20L)
                        .with(authentication(userAuthentication(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.qrToken").value("qr-token"));

        verify(meetVerificationQueryService).getMeetQrByPost(1L, 20L);
    }

    @Test
    @DisplayName("QR 스캔 API는 사용자 ID와 매치 ID, QR 토큰을 서비스로 전달한다")
    void createQrScan_shouldReturnScanResult() throws Exception {
        LocalDateTime completedAt = LocalDateTime.of(2026, 1, 1, 12, 0);
        when(meetVerificationCommandService.createQrScan(eq(1L), eq(10L), any(QrScanRequestDto.class)))
                .thenReturn(new QrScanResponseDto(10L, VerificationStatus.DONE, MatchStatus.COMPLETED, completedAt, 5000));

        mockMvc.perform(post("/api/v1/matches/{matchId}/qr/scan", 10L)
                        .with(authentication(userAuthentication(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "qrToken": "qr-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.verificationStatus").value("DONE"));

        verify(meetVerificationCommandService).createQrScan(eq(1L), eq(10L), any(QrScanRequestDto.class));
    }

    @Test
    @DisplayName("만남 인증 상태 조회 API는 사용자 ID와 매치 ID로 인증 상태를 조회한다")
    void getMeetVerification_shouldReturnVerification() throws Exception {
        LocalDateTime verifiedAt = LocalDateTime.of(2026, 1, 1, 12, 0);
        MeetVerificationResponseDto.ParticipantVerificationDto participant =
                new MeetVerificationResponseDto.ParticipantVerificationDto(10L, "상대", true, verifiedAt, VerificationStatus.VERIFIED, false, null);
        when(meetVerificationQueryService.getMeetVerification(1L, 10L))
                .thenReturn(new MeetVerificationResponseDto(
                        10L,
                        VerificationStatus.VERIFIED,
                        "작성자",
                        verifiedAt,
                        List.of(participant),
                        true,
                        verifiedAt.plusMinutes(30),
                        null,
                        null));

        mockMvc.perform(get("/api/v1/matches/{matchId}/verification", 10L)
                        .with(authentication(userAuthentication(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"));

        verify(meetVerificationQueryService).getMeetVerification(1L, 10L);
    }

    @Test
    @DisplayName("만남 시간 연장 요청 API는 사용자 ID와 매치 ID를 서비스로 전달하고 201을 반환한다")
    void createMeetExtension_shouldReturnCreatedExtension() throws Exception {
        LocalDateTime originalMeetAt = LocalDateTime.of(2026, 1, 1, 12, 0);
        LocalDateTime requestedAt = LocalDateTime.of(2026, 1, 1, 11, 55);
        when(meetVerificationCommandService.createMeetExtension(1L, 10L))
                .thenReturn(new CreateMeetExtensionResponseDto(
                        10L,
                        ExtensionStatus.REQUESTED,
                        1L,
                        "요청자",
                        originalMeetAt,
                        originalMeetAt.plusMinutes(10),
                        requestedAt,
                        requestedAt.plusMinutes(5)));

        mockMvc.perform(post("/api/v1/matches/{matchId}/extension/request", 10L)
                        .with(authentication(userAuthentication(1L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.extensionStatus").value("REQUESTED"));

        verify(meetVerificationCommandService).createMeetExtension(1L, 10L);
    }

    @Test
    @DisplayName("만남 시간 연장 수락 API는 사용자 ID와 매치 ID를 서비스로 전달한다")
    void acceptMeetExtension_shouldReturnAcceptedExtension() throws Exception {
        LocalDateTime originalMeetAt = LocalDateTime.of(2026, 1, 1, 12, 0);
        when(meetVerificationCommandService.acceptMeetExtension(1L, 10L))
                .thenReturn(new AcceptMeetExtensionResponseDto(
                        10L,
                        ExtensionStatus.ACCEPTED,
                        originalMeetAt,
                        originalMeetAt.plusMinutes(10),
                        true,
                        LocalDateTime.of(2026, 1, 1, 11, 56)));

        mockMvc.perform(patch("/api/v1/matches/{matchId}/extension/accept", 10L)
                        .with(authentication(userAuthentication(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.extensionStatus").value("ACCEPTED"));

        verify(meetVerificationCommandService).acceptMeetExtension(1L, 10L);
    }

    @Test
    @DisplayName("만남 시간 연장 거절 API는 사용자 ID와 매치 ID를 서비스로 전달한다")
    void rejectMeetExtension_shouldReturnRejectedExtension() throws Exception {
        when(meetVerificationCommandService.rejectMeetExtension(1L, 10L))
                .thenReturn(new RejectMeetExtensionResponseDto(
                        10L,
                        ExtensionStatus.REJECTED,
                        LocalDateTime.of(2026, 1, 1, 11, 56)));

        mockMvc.perform(patch("/api/v1/matches/{matchId}/extension/reject", 10L)
                        .with(authentication(userAuthentication(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.extensionStatus").value("REJECTED"));

        verify(meetVerificationCommandService).rejectMeetExtension(1L, 10L);
    }

    @Test
    @DisplayName("만남 시간 연장 상태 조회 API는 사용자 ID와 매치 ID로 연장 상태를 조회한다")
    void getMeetExtension_shouldReturnExtensionStatus() throws Exception {
        LocalDateTime originalMeetAt = LocalDateTime.of(2026, 1, 1, 12, 0);
        LocalDateTime requestedAt = LocalDateTime.of(2026, 1, 1, 11, 55);
        when(meetVerificationQueryService.getMeetExtension(1L, 10L))
                .thenReturn(new GetMeetExtensionResponseDto(
                        10L,
                        ExtensionStatus.REQUESTED,
                        1L,
                        "요청자",
                        true,
                        originalMeetAt,
                        originalMeetAt.plusMinutes(10),
                        requestedAt,
                        requestedAt.plusMinutes(5)));

        mockMvc.perform(get("/api/v1/matches/{matchId}/extension", 10L)
                        .with(authentication(userAuthentication(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.extensionStatus").value("REQUESTED"));

        verify(meetVerificationQueryService).getMeetExtension(1L, 10L);
    }
}
