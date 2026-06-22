package com.example.team3final.domain.match.controller;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.match.dto.request.CancelMatchRequestDto;
import com.example.team3final.domain.match.dto.response.CreateMatchResponseDto;
import com.example.team3final.domain.match.enums.MatchStatus;
import com.example.team3final.domain.match.service.MatchCommandService;
import com.example.team3final.domain.match.service.MatchQueryService;
import com.example.team3final.domain.meet.service.MeetVerificationInternalService;
import com.example.team3final.test.controller.ControllerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
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
@DisplayName("매칭 컨트롤러 통합 테스트")
class MatchControllerTest extends ControllerTestSupport {

    @Mock
    private MatchCommandService matchCommandService;

    @Mock
    private MatchQueryService matchQueryService;

    @Mock
    private MeetVerificationInternalService meetVerificationInternalService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvcFor(new MatchController(matchCommandService, matchQueryService, meetVerificationInternalService));
    }

    @Test
    @DisplayName("매칭 신청 API는 매칭 생성 후 만남 인증을 초기화하고 201을 반환한다")
    void createMatch_shouldCreatePendingVerification() throws Exception {
        when(matchCommandService.createMatch(10L, 1L))
                .thenReturn(new CreateMatchResponseDto(
                        20L, 10L, 2L, "author", 1L, "applicant",
                        500, 500, MatchStatus.MATCHED, 30L, LocalDateTime.now()
                ));

        mockMvc.perform(post("/api/v1/posts/10/matches")
                        .with(authentication(userAuthentication(1L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        verify(matchCommandService).createMatch(10L, 1L);
        verify(meetVerificationInternalService).createPendingVerification(20L);
    }

    @Test
    @DisplayName("매칭 상세 조회 API는 매칭 ID와 사용자 ID를 서비스로 전달한다")
    void getMatch_shouldDelegateMatchIdAndUserId() throws Exception {
        mockMvc.perform(get("/api/v1/matches/20")
                        .with(authentication(userAuthentication(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(matchQueryService).getMatch(20L, 1L);
    }

    @Test
    @DisplayName("내 매칭 목록 조회 API는 상태 필터와 최대 50개 페이지 크기를 서비스로 전달한다")
    void getMatches_shouldBindStatusAndClampSize() throws Exception {
        when(matchQueryService.getMatches(eq(1L), eq(MatchStatus.MATCHED), any(Pageable.class)))
                .thenReturn(new PageResponseDto<>(List.of(), 0, 50, 0, 0, false));

        mockMvc.perform(get("/api/v1/matches/me")
                        .with(authentication(userAuthentication(1L)))
                        .param("status", "MATCHED")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(matchQueryService).getMatches(eq(1L), eq(MatchStatus.MATCHED), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    @DisplayName("매칭 취소 API는 취소 요청 본문과 사용자 ID를 서비스로 전달한다")
    void cancelMatch_shouldDelegateCancelRequest() throws Exception {
        mockMvc.perform(patch("/api/v1/matches/20/cancel")
                        .with(authentication(userAuthentication(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"personal reason\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(matchCommandService).cancelMatch(eq(20L), eq(1L), any(CancelMatchRequestDto.class));
    }
}
