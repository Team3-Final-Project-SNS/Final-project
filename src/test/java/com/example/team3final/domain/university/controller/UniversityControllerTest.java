package com.example.team3final.domain.university.controller;

import com.example.team3final.domain.university.dto.response.UniversityResponseDto;
import com.example.team3final.domain.university.service.UniversityService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UniversityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UniversityService universityService;

    @Test
    @DisplayName("대학 목록 조회 - 성공")
    void getUniversities_Success() throws Exception {
        // given
        UniversityResponseDto responseDto = UniversityResponseDto.builder()
                .universityId(1L)
                .universityName("Test University")
                .eDomain("test.ac.kr")
                .build();
        given(universityService.getUniversities()).willReturn(List.of(responseDto));

        // when & then
        mockMvc.perform(get("/api/v1/universities")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].universityName").value("Test University"))
                .andExpect(jsonPath("$.data[0].eDomain").value("test.ac.kr"));
    }
}

