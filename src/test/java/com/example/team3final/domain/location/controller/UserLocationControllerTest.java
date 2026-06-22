package com.example.team3final.domain.location.controller;

import com.example.team3final.domain.location.dto.LocationDto;
import com.example.team3final.domain.location.dto.request.UpdateLocationRequestDto;
import com.example.team3final.domain.location.dto.response.GetLocationResponseDto;
import com.example.team3final.domain.location.dto.response.UpdateLocationResponseDto;
import com.example.team3final.domain.location.enums.LocationRole;
import com.example.team3final.domain.location.service.UserLocationService;
import com.example.team3final.test.controller.ControllerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("사용자 위치 컨트롤러 통합 테스트")
class UserLocationControllerTest extends ControllerTestSupport {

    @Mock
    private UserLocationService userLocationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvcFor(new UserLocationController(userLocationService));
    }

    @Test
    @DisplayName("내 위치 업데이트 API는 매치 ID와 사용자 ID, 위치 정보를 서비스로 전달한다")
    void updateMyLocation_shouldReturnUpdatedLocation() throws Exception {
        LocalDateTime updatedAt = LocalDateTime.of(2026, 1, 1, 12, 0);
        when(userLocationService.updateMyLocation(eq(10L), eq(1L), any(UpdateLocationRequestDto.class)))
                .thenReturn(new UpdateLocationResponseDto(
                        10L,
                        1L,
                        new BigDecimal("37.5665"),
                        new BigDecimal("126.9780"),
                        updatedAt));

        mockMvc.perform(put("/api/v1/matches/{matchId}/location", 10L)
                        .with(authentication(userAuthentication(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "latitude": 37.5665,
                                  "longitude": 126.9780
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.matchId").value(10));

        verify(userLocationService).updateMyLocation(eq(10L), eq(1L), any(UpdateLocationRequestDto.class));
    }

    @Test
    @DisplayName("매치 위치 조회 API는 매치 ID와 사용자 ID로 내 위치와 상대 위치를 조회한다")
    void getLocations_shouldReturnLocations() throws Exception {
        LocalDateTime updatedAt = LocalDateTime.of(2026, 1, 1, 12, 0);
        LocationDto myLocation = new LocationDto(new BigDecimal("37.5665"), new BigDecimal("126.9780"), updatedAt, LocationRole.AUTHOR);
        LocationDto opponentLocation = new LocationDto(new BigDecimal("37.5651"), new BigDecimal("126.9895"), updatedAt, LocationRole.APPLICANT);
        when(userLocationService.getLocations(10L, 1L))
                .thenReturn(new GetLocationResponseDto(myLocation, opponentLocation, List.of(opponentLocation)));

        mockMvc.perform(get("/api/v1/matches/{matchId}/location", 10L)
                        .with(authentication(userAuthentication(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.myLocation.role").value("AUTHOR"));

        verify(userLocationService).getLocations(10L, 1L);
    }
}
