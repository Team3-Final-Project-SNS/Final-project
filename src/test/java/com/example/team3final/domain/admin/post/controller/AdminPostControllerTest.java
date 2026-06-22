package com.example.team3final.domain.admin.post.controller;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.admin.post.dto.request.AdminDeletePostRequestDto;
import com.example.team3final.domain.admin.post.service.AdminPostService;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.test.controller.ControllerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("관리자 게시글 컨트롤러 통합 테스트")
class AdminPostControllerTest extends ControllerTestSupport {

    @Mock
    private AdminPostService adminPostService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvcFor(new AdminPostController(adminPostService));
    }

    @Test
    @DisplayName("관리자 게시글 목록 조회 API는 필터 조건을 서비스로 전달한다")
    void getPosts_shouldBindFiltersAndDelegate() throws Exception {
        when(adminPostService.getPosts(eq(1L), eq(2L), eq("author"), eq(PostStatus.OPEN),
                eq(false), eq("lunch"), any(Pageable.class)))
                .thenReturn(new PageResponseDto<>(List.of(), 0, 20, 0, 0, false));

        mockMvc.perform(get("/api/v1/admin/posts")
                        .with(authentication(adminAuthentication(1L)))
                        .param("universityId", "2")
                        .param("authorNickname", "author")
                        .param("status", "OPEN")
                        .param("deleted", "false")
                        .param("keyword", "lunch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(adminPostService).getPosts(eq(1L), eq(2L), eq("author"), eq(PostStatus.OPEN),
                eq(false), eq("lunch"), any(Pageable.class));
    }

    @Test
    @DisplayName("관리자 게시글 상세 조회 API는 관리자 ID와 게시글 ID를 서비스로 전달한다")
    void getPost_shouldPassAdminIdAndPostId() throws Exception {
        mockMvc.perform(get("/api/v1/admin/posts/10")
                        .with(authentication(adminAuthentication(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(adminPostService).getPost(1L, 10L);
    }

    @Test
    @DisplayName("관리자 게시글 강제 삭제 API는 삭제 사유 요청을 서비스로 전달한다")
    void deletePost_shouldPassAdminIdPostIdAndRequest() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/posts/10")
                        .with(authentication(adminAuthentication(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportId\":5,\"reason\":\"policy violation\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(adminPostService).deletePost(eq(1L), eq(10L), any(AdminDeletePostRequestDto.class));
    }

    @Test
    @DisplayName("관리자 게시글 복구 API는 관리자 ID와 게시글 ID를 서비스로 전달한다")
    void restorePost_shouldPassAdminIdAndPostId() throws Exception {
        mockMvc.perform(post("/api/v1/admin/posts/10/restore")
                        .with(authentication(adminAuthentication(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(adminPostService).restorePost(1L, 10L);
    }
}
