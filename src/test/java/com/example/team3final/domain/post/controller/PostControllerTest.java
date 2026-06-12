package com.example.team3final.domain.post.controller;

import com.example.team3final.domain.post.dto.request.CreatePostRequestDto;
import com.example.team3final.domain.post.dto.request.UpdatePostRequestDto;
import com.example.team3final.domain.post.dto.response.CreatePostResponseDto;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.post.service.PostService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import com.example.team3final.test.security.WithMockCustomUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PostController.class)
@AutoConfigureMockMvc(addFilters = false)
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostService postService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("게시글 생성 API - 201 반환")
    @WithMockCustomUser
    void createPost_ApiTest() throws Exception {
        // given
        CreatePostRequestDto request = CreatePostRequestDto.builder()
                .meetAt(LocalDateTime.now().plusDays(1))
                .placeName("place")
                .placeLat(new BigDecimal("37.0"))
                .placeLng(new BigDecimal("127.0"))
                .content("content")
                .authorDeposit(1000)
                .maxApplicants(2)
                .build();
        
        CreatePostResponseDto response = new CreatePostResponseDto(
                1L, 1L, "nickname", LocalDateTime.now(), "place", new BigDecimal("37.0"), new BigDecimal("127.0"), "content", 1000, PostStatus.OPEN, LocalDateTime.now());

        given(postService.createPost(anyLong(), any())).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("게시글 목록 조회 API - 200 반환")
    @WithMockCustomUser
    void getPosts_ApiTest() throws Exception {
        // given
        given(postService.getPosts(anyLong(), any(), any())).willReturn(null);

        // when & then
        mockMvc.perform(get("/api/v1/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("게시글 상세 조회 API - 200 반환")
    @WithMockCustomUser
    void getPost_ApiTest() throws Exception {
        // given
        given(postService.getPost(anyLong(), anyLong())).willReturn(null);

        // when & then
        mockMvc.perform(get("/api/v1/posts/{postId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("게시글 수정 API - 200 반환")
    @WithMockCustomUser
    void updatePost_ApiTest() throws Exception {
        // given
        UpdatePostRequestDto request = new UpdatePostRequestDto(
                LocalDateTime.now().plusDays(1),
                "place",
                new BigDecimal("37.0"),
                new BigDecimal("127.0"),
                "updated content",
                1000
        );
        given(postService.updatePost(anyLong(), anyLong(), any())).willReturn(null);

        // when & then
        mockMvc.perform(patch("/api/v1/posts/{postId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("게시글 삭제 API - 200 반환")
    @WithMockCustomUser
    void deletePost_ApiTest() throws Exception {
        // given
        given(postService.deletePost(anyLong(), anyLong())).willReturn(null);

        // when & then
        mockMvc.perform(delete("/api/v1/posts/{postId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("삭제 게시글 사유 조회 API - 200반환")
    @WithMockCustomUser
    void getDeletedPostReason_ApiTest() throws Exception {
        // given
        given(postService.getDeletedPostReason(anyLong(), anyLong())).willReturn(null);

        // when & then
        mockMvc.perform(get("/api/v1/posts/{postId}/delete-reason", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }
}
