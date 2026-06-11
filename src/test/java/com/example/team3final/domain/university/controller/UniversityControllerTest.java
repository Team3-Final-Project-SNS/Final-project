package com.example.team3final.domain.university.controller;

import com.example.team3final.domain.university.dto.response.UniversityResponseDto;
import com.example.team3final.domain.university.service.UniversityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UniversityController.class)
@AutoConfigureMockMvc(addFilters = false)
class UniversityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UniversityService universityService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("API test")
    void getUniversities_ApiTest() throws Exception {
        // given
        UniversityResponseDto responseDto = new UniversityResponseDto(1L, "Test Univ", "test.ac.kr");
        given(universityService.getUniversities()).willReturn(List.of(responseDto));

        // when & then
        mockMvc.perform(get("/api/v1/universities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data[0].universityName").value("Test Univ"));
    }
}
