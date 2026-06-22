package com.example.team3final.domain.admin.meet.controller;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.admin.meet.service.AdminMeetVerificationService;
import com.example.team3final.test.controller.ControllerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("관리자 만남 인증 컨트롤러 통합 테스트")
class AdminMeetVerificationControllerTest extends ControllerTestSupport {

    @Mock
    private AdminMeetVerificationService adminMeetVerificationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvcFor(new AdminMeetVerificationController(adminMeetVerificationService));
    }

    @Test
    @DisplayName("노쇼 후보 조회 API는 페이지 조건을 서비스로 전달한다")
    void getNoShowCandidates_shouldDelegatePageable() throws Exception {
        when(adminMeetVerificationService.getNoShowCandidates(any(Pageable.class)))
                .thenReturn(new PageResponseDto<>(List.of(), 1, 30, 0, 0, false));

        mockMvc.perform(get("/api/v1/admin/no-show-candidates")
                        .param("page", "1")
                        .param("size", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(adminMeetVerificationService).getNoShowCandidates(pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(30);
    }
}
