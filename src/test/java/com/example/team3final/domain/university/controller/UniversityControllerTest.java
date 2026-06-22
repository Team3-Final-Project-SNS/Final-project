package com.example.team3final.domain.university.controller;

import com.example.team3final.domain.university.service.UniversityQueryService;
import com.example.team3final.test.controller.ControllerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("대학 컨트롤러 통합 테스트")
class UniversityControllerTest extends ControllerTestSupport {

    @Mock
    private UniversityQueryService universityQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvcFor(new UniversityController(universityQueryService));
    }

    @Test
    @DisplayName("대학 목록 조회 API는 서비스 결과를 성공 응답으로 반환한다")
    void getUniversities_shouldReturnSuccessResponse() throws Exception {
        when(universityQueryService.getUniversities()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/universities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(universityQueryService).getUniversities();
    }
}
