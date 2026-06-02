package com.example.team3final.domain.post.controller;

import com.example.team3final.domain.post.dto.request.CreatePostRequestDto;
import com.example.team3final.domain.post.dto.response.CreatePostResponseDto;
import com.example.team3final.domain.post.service.PostService;
import com.example.team3final.domain.user.entity.User;
import com.example.team3final.domain.user.service.UserDetailsImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PostService postService;

    @Test
    @DisplayName("게시글 생성 - 성공")
    void createPost_Success() throws Exception {
        // given
        User user = User.builder().email("test@test.ac.kr").build();
        ReflectionTestUtils.setField(user, "id", 1L);
        UserDetailsImpl userDetails = new UserDetailsImpl(user);

        CreatePostRequestDto request = CreatePostRequestDto.builder()
                .meetAt(LocalDateTime.now().plusHours(1))
                .placeName("정문")
                .placeLat(new java.math.BigDecimal("37.5"))
                .placeLng(new java.math.BigDecimal("127.0"))
                .authorDeposit(200)
                .maxApplicants(2)
                .build();

        CreatePostResponseDto responseDto = new CreatePostResponseDto(
                100L, 1L, "Nick", null, null, null, null, null, 0, null, null
        );
        given(postService.createPost(anyLong(), any())).willReturn(responseDto);

        // when & then
        mockMvc.perform(post("/api/v1/posts")
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.postId").value(100));
    }
}

