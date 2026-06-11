package com.example.team3final.domain.admin.user.controller;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.admin.user.dto.response.AdminGetUsersResponseDto;
import com.example.team3final.domain.admin.user.service.AdminUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.example.team3final.test.security.WithMockAdmin;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminUserService adminUserService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("API test")
    @WithMockAdmin
    void getUsers_ApiTest() throws Exception {
        // given
        PageResponseDto<AdminGetUsersResponseDto> response = PageResponseDto.from(new PageImpl<>(List.of()));
        given(adminUserService.getUsers(any(), anyString(), any())).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("API test")
    @WithMockAdmin
    void suspendUser_ApiTest() throws Exception {
        // given
        given(adminUserService.suspendUser(anyLong(), anyLong(), any())).willReturn(null);

        // when & then
        mockMvc.perform(patch("/api/v1/admin/users/{userId}/suspend", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"policy violation\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("API test")
    @WithMockAdmin
    void reinstateUser_ApiTest() throws Exception {
        // given
        given(adminUserService.reinstateUser(anyLong(), anyLong(), any())).willReturn(null);

        // when & then
        mockMvc.perform(patch("/api/v1/admin/users/{userId}/reinstate", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"appeal accepted\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }
}
