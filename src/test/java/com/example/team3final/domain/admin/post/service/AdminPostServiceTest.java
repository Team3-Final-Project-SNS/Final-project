package com.example.team3final.domain.admin.post.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.post.dto.request.AdminDeletePostRequestDto;
import com.example.team3final.domain.admin.post.dto.response.AdminDeletePostResponseDto;
import com.example.team3final.domain.admin.post.dto.response.AdminGetPostsResponseDto;
import com.example.team3final.domain.admin.post.dto.response.AdminRestorePostResponseDto;
import com.example.team3final.domain.admin.repository.AdminRepository;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.report.service.ReportService;
import com.example.team3final.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AdminPostServiceTest {

    @InjectMocks
    private AdminPostServiceImpl adminPostService;

    @Mock
    private AdminRepository adminRepository;
    @Mock
    private PostService postService;
    @Mock
    private ReportService reportService;
    @Mock
    private UserService userService;

    @Test
    @DisplayName("관리자 게시글 목록 조회 - 성공 (빈 목록)")
    void getPosts_Empty_Success() {
        // given
        Long adminId = 1L;
        Pageable pageable = PageRequest.of(0, 10);
        given(adminRepository.findById(adminId)).willReturn(Optional.of(mock(Admin.class)));

        Post post = Post.builder().authorId(10L).build();
        given(postService.getPostsForAdmin(any(), any(), any(), any())).willReturn(new PageImpl<>(List.of(post)));
        given(userService.getUserNicknameMap(any())).willReturn(Map.of(10L, "nickname"));

        // when
        PageResponseDto<AdminGetPostsResponseDto> result = adminPostService.getPosts(adminId, null, null, null, null, pageable);

        // then
        assertThat(result.content()).hasSize(1);
    }

    @Test
    @DisplayName("관리자 게시글 강제 삭제 - 성공")
    void deletePost_Success() {
        Admin admin = mock(Admin.class);
        Post post = createPost(100L, 10L);
        AdminDeletePostRequestDto request = new AdminDeletePostRequestDto();
        ReflectionTestUtils.setField(request, "reason", "reason");
        given(admin.isActiveAndSuperAdmin()).willReturn(true);
        given(adminRepository.findById(1L)).willReturn(Optional.of(admin));
        given(postService.getPostByIdWithLock(100L)).willReturn(post);
        given(reportService.existsPendingReport(100L)).willReturn(false);
        given(postService.forceDeletePost(post, "reason")).willReturn(1000);

        AdminDeletePostResponseDto result = adminPostService.deletePost(1L, 100L, request);

        assertThat(result.postId()).isEqualTo(100L);
        assertThat(result.refundedPoint()).isEqualTo(1000);
    }

    @Test
    @DisplayName("관리자 게시글 복구 - 성공")
    void restorePost_Success() {
        Admin admin = mock(Admin.class);
        Post post = createPost(100L, 10L);
        post.deleteAndReason("reason");
        given(admin.isActiveAndSuperAdmin()).willReturn(true);
        given(adminRepository.findById(1L)).willReturn(Optional.of(admin));
        given(postService.getPostByIdIncludingDeleted(100L)).willReturn(post);
        given(postService.restorePost(post)).willReturn(1000);

        AdminRestorePostResponseDto result = adminPostService.restorePost(1L, 100L);

        assertThat(result.postId()).isEqualTo(100L);
        assertThat(result.redepositedPoint()).isEqualTo(1000);
    }

    private Post createPost(Long id, Long authorId) {
        Post post = Post.builder()
                .authorId(authorId)
                .meetAt(LocalDateTime.now().plusDays(1))
                .placeName("place")
                .placeLat(new BigDecimal("37.0"))
                .placeLng(new BigDecimal("127.0"))
                .content("content")
                .authorDeposit(1000)
                .maxApplicants(2)
                .build();
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }
}
