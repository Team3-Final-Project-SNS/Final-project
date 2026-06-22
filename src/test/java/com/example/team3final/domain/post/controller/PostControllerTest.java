package com.example.team3final.domain.post.controller;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.post.dto.request.CreatePostRequestDto;
import com.example.team3final.domain.post.dto.request.UpdatePostRequestDto;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.post.service.PostCommandService;
import com.example.team3final.domain.post.service.PostQueryService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("게시글 컨트롤러 통합 테스트")
class PostControllerTest extends ControllerTestSupport {

    @Mock
    private PostCommandService postCommandService;

    @Mock
    private PostQueryService postQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvcFor(new PostController(postCommandService, postQueryService));
    }

    @Test
    @DisplayName("게시글 작성 API는 인증 사용자 ID와 작성 요청을 서비스로 전달하고 201을 반환한다")
    void createPost_shouldReturnCreatedAndDelegate() throws Exception {
        mockMvc.perform(post("/api/v1/posts")
                        .with(authentication(userAuthentication(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "meetAt": "2099-01-01T12:00:00",
                                  "placeName": "main gate",
                                  "placeLat": 37.0,
                                  "placeLng": 127.0,
                                  "content": "lunch",
                                  "authorDeposit": 500,
                                  "maxApplicants": 2
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        verify(postCommandService).createPost(eq(1L), any(CreatePostRequestDto.class));
    }

    @Test
    @DisplayName("게시글 목록 조회 API는 상태, 정렬, 페이지 크기를 바인딩해 서비스로 전달한다")
    void getPosts_shouldBindStatusSortAndPageable() throws Exception {
        when(postQueryService.getPosts(eq(1L), eq(PostStatus.OPEN), any(Pageable.class)))
                .thenReturn(new PageResponseDto<>(List.of(), 0, 50, 0, 0, false));

        mockMvc.perform(get("/api/v1/posts")
                        .with(authentication(userAuthentication(1L)))
                        .param("status", "OPEN")
                        .param("size", "100")
                        .param("sort", "LATEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(postQueryService).getPosts(eq(1L), eq(PostStatus.OPEN), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    @DisplayName("게시글 상세 조회 API는 게시글 ID와 현재 사용자 ID를 서비스로 전달한다")
    void getPost_shouldDelegatePostIdAndUserId() throws Exception {
        mockMvc.perform(get("/api/v1/posts/10")
                        .with(authentication(userAuthentication(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(postQueryService).getPost(10L, 1L);
    }

    @Test
    @DisplayName("게시글 수정 API는 게시글 ID, 사용자 ID, 수정 요청을 서비스로 전달한다")
    void updatePost_shouldDelegateUpdateRequest() throws Exception {
        mockMvc.perform(patch("/api/v1/posts/10")
                        .with(authentication(userAuthentication(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "meetAt": "2099-01-01T12:00:00",
                                  "placeName": "library",
                                  "placeLat": 37.0,
                                  "placeLng": 127.0,
                                  "content": "dinner",
                                  "authorDeposit": 600
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(postCommandService).updatePost(eq(10L), eq(1L), any(UpdatePostRequestDto.class));
    }

    @Test
    @DisplayName("게시글 삭제 API는 게시글 ID와 사용자 ID를 서비스로 전달한다")
    void deletePost_shouldDelegatePostIdAndUserId() throws Exception {
        mockMvc.perform(delete("/api/v1/posts/10")
                        .with(authentication(userAuthentication(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(postCommandService).deletePost(10L, 1L);
    }

    @Test
    @DisplayName("삭제된 게시글 사유 조회 API는 게시글 ID와 사용자 ID를 서비스로 전달한다")
    void getDeletedPostReason_shouldDelegatePostIdAndUserId() throws Exception {
        mockMvc.perform(get("/api/v1/posts/10/delete-reason")
                        .with(authentication(userAuthentication(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(postQueryService).getDeletedPostReason(10L, 1L);
    }
}
