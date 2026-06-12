package com.example.team3final.domain.admin.post.controller;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.admin.post.dto.response.AdminGetPostsResponseDto;
import com.example.team3final.domain.admin.post.service.AdminPostService;
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
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminPostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminPostService adminPostService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("관리자 게시글 목록 조회 API test")
    @WithMockAdmin
    void getPosts_ApiTest() throws Exception {
        // given
        PageResponseDto<AdminGetPostsResponseDto> response = PageResponseDto.from(new PageImpl<>(List.of()));
        given(adminPostService.getPosts(anyLong(), any(), any(), any(), any(), any())).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/admin/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("관리자 게시글 강제 삭제 API test")
    @WithMockAdmin
    void deletePost_ApiTest() throws Exception {
        // given
        given(adminPostService.deletePost(anyLong(), anyLong(), any())).willReturn(null);

        // when & then
        mockMvc.perform(delete("/api/v1/admin/posts/{postId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"inappropriate content\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("관리자 게시글 상세 조회 API test")
    @WithMockAdmin
    void getPost_ApiTest() throws Exception {
        // given
        given(adminPostService.getPost(anyLong(), anyLong())).willReturn(null);

        // when & then
        mockMvc.perform(get("/api/v1/admin/posts/{postId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("관리자 게시글 복구 API test")
    @WithMockAdmin
    void restorePost_ApiTest() throws Exception {
        // given
        given(adminPostService.restorePost(anyLong(), anyLong())).willReturn(null);

        // when & then
        mockMvc.perform(post("/api/v1/admin/posts/{postId}/restore", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }
}
