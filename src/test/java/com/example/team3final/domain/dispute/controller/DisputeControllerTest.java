package com.example.team3final.domain.dispute.controller;

import com.example.team3final.domain.dispute.dto.request.CreateDisputeRequestDto;
import com.example.team3final.domain.dispute.dto.response.CreateDisputeResponseDto;
import com.example.team3final.domain.dispute.dto.response.DisputeResponseDto;
import com.example.team3final.domain.dispute.dto.response.MyDisputeResponseDto;
import com.example.team3final.domain.dispute.enums.DisputeStatus;
import com.example.team3final.domain.dispute.enums.DisputeType;
import com.example.team3final.domain.dispute.service.DisputeCommandService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("이의제기 컨트롤러 통합 테스트")
class DisputeControllerTest extends ControllerTestSupport {

    @Mock
    private DisputeCommandService disputeCommandService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvcFor(new DisputeController(disputeCommandService));
    }

    @Test
    @DisplayName("이의제기 제출 API는 매치 ID와 사용자 ID, 요청 본문을 서비스로 전달하고 201을 반환한다")
    void createDispute_shouldReturnCreatedAndDelegate() throws Exception {
        LocalDateTime submittedAt = LocalDateTime.of(2026, 1, 1, 12, 0);
        when(disputeCommandService.createDispute(eq(10L), eq(1L), any(CreateDisputeRequestDto.class)))
                .thenReturn(new CreateDisputeResponseDto(100L, 10L, DisputeType.GPS_ERROR, DisputeStatus.SUBMITTED, submittedAt));

        mockMvc.perform(post("/api/v1/matches/{matchId}/disputes", 10L)
                        .with(authentication(userAuthentication(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "disputeType": "GPS_ERROR",
                                  "reason": "GPS 인증에 실패했습니다."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.disputeId").value(100));

        verify(disputeCommandService).createDispute(eq(10L), eq(1L), any(CreateDisputeRequestDto.class));
    }

    @Test
    @DisplayName("내 이의제기 상세 조회 API는 매치 ID와 사용자 ID로 이의제기 상세를 조회한다")
    void getDispute_shouldReturnDispute() throws Exception {
        LocalDateTime submittedAt = LocalDateTime.of(2026, 1, 1, 12, 0);
        when(disputeCommandService.getDispute(10L, 1L))
                .thenReturn(new DisputeResponseDto(
                        100L,
                        10L,
                        DisputeType.GPS_ERROR,
                        "GPS 인증에 실패했습니다.",
                        DisputeStatus.SUBMITTED,
                        null,
                        submittedAt,
                        null,
                        null));

        mockMvc.perform(get("/api/v1/matches/{matchId}/disputes/me", 10L)
                        .with(authentication(userAuthentication(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.disputeId").value(100));

        verify(disputeCommandService).getDispute(10L, 1L);
    }

    @Test
    @DisplayName("내 이의제기 목록 조회 API는 사용자 ID로 이의제기 목록을 조회한다")
    void getMyDisputes_shouldReturnDisputeList() throws Exception {
        LocalDateTime submittedAt = LocalDateTime.of(2026, 1, 1, 12, 0);
        when(disputeCommandService.getMyDisputes(1L))
                .thenReturn(List.of(new MyDisputeResponseDto(
                        100L,
                        10L,
                        DisputeType.GPS_ERROR,
                        DisputeStatus.SUBMITTED,
                        submittedAt)));

        mockMvc.perform(get("/api/v1/disputes/me")
                        .with(authentication(userAuthentication(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].disputeId").value(100));

        verify(disputeCommandService).getMyDisputes(1L);
    }

    @Test
    @DisplayName("이의제기 재제출 API는 매치 ID와 사용자 ID, 요청 본문을 서비스로 전달하고 201을 반환한다")
    void reCreateDispute_shouldReturnCreatedAndDelegate() throws Exception {
        LocalDateTime submittedAt = LocalDateTime.of(2026, 1, 1, 12, 0);
        when(disputeCommandService.reCreateDispute(eq(10L), eq(1L), any(CreateDisputeRequestDto.class)))
                .thenReturn(new CreateDisputeResponseDto(101L, 10L, DisputeType.GPS_ERROR, DisputeStatus.SUBMITTED, submittedAt));

        mockMvc.perform(post("/api/v1/matches/{matchId}/disputes/resubmit", 10L)
                        .with(authentication(userAuthentication(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "disputeType": "GPS_ERROR",
                                  "reason": "추가 자료를 제출합니다."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.disputeId").value(101));

        verify(disputeCommandService).reCreateDispute(eq(10L), eq(1L), any(CreateDisputeRequestDto.class));
    }
}
