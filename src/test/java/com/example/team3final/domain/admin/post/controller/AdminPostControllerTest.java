package com.example.team3final.domain.admin.post.controller;

import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.post.dto.request.AdminDeletePostRequestDto;
import com.example.team3final.domain.admin.post.dto.response.AdminDeletePostResponseDto;
import com.example.team3final.domain.admin.post.service.AdminPostService;
import com.example.team3final.domain.admin.security.AdminDetailsImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminPostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminPostService adminPostService;

    @Test
    @DisplayName("게시글 강제 삭제 - 성공")
    void deletePost_Success() throws Exception {
        // given
        Admin admin = Admin.builder().email("admin@test.com").password("pass").build();
        ReflectionTestUtils.setField(admin, "id", 1L);
        ReflectionTestUtils.setField(admin, "role", com.example.team3final.domain.admin.enums.AdminRole.SUPER_ADMIN);
        ReflectionTestUtils.setField(admin, "isActive", true);
        AdminDetailsImpl adminDetails = new AdminDetailsImpl(admin);

        AdminDeletePostRequestDto request = new AdminDeletePostRequestDto();
        ReflectionTestUtils.setField(request, "reportId", 50L);
        ReflectionTestUtils.setField(request, "reason", "Violation");

        AdminDeletePostResponseDto responseDto = new AdminDeletePostResponseDto(100L, 50L, "Violation", 500, LocalDateTime.now());
        given(adminPostService.deletePost(anyLong(), anyLong(), any())).willReturn(responseDto);

        // when & then
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(adminDetails, null, adminDetails.getAuthorities())
        );
        mockMvc.perform(delete("/api/v1/admin/posts/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.postId").value(100));
    }
}

