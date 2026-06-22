package com.example.team3final.domain.admin.post.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.AdminException;
import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.enums.AdminRole;
import com.example.team3final.domain.admin.post.dto.request.AdminDeletePostRequestDto;
import com.example.team3final.domain.admin.post.dto.response.AdminDeletePostResponseDto;
import com.example.team3final.domain.admin.repository.AdminRepository;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.enums.PostStatus;
import com.example.team3final.domain.post.service.PostInternalService;
import com.example.team3final.domain.post.service.PostModerationService;
import com.example.team3final.domain.report.service.ReportInternalService;
import com.example.team3final.domain.report.service.ReportModerationService;
import com.example.team3final.domain.user.service.UserInternalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("관리자 게시글 서비스 단위 테스트")
class AdminPostServiceTest {

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private PostInternalService postInternalService;

    @Mock
    private ReportInternalService reportInternalService;

    @Mock
    private ReportModerationService reportModerationService;

    @Mock
    private UserInternalService userInternalService;

    @Mock
    private PostModerationService postModerationService;

    @InjectMocks
    private AdminPostServiceImpl adminPostService;

    @Test
    @DisplayName("관리자가 게시글 목록을 조회하면 운영용 게시글 조회 결과를 페이지 응답으로 반환한다")
    void getPosts_shouldReturnPageResponse() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin()));
        when(postModerationService.getPostsForAdmin(null, PostStatus.OPEN, false, "밥", pageable))
                .thenReturn(Page.empty(pageable));

        PageResponseDto<?> response = adminPostService.getPosts(1L, null, null, PostStatus.OPEN, false, "밥", pageable);

        assertThat(response.totalElements()).isZero();
        verify(postModerationService).getPostsForAdmin(null, PostStatus.OPEN, false, "밥", pageable);
    }

    @Test
    @DisplayName("관리자가 없으면 게시글 목록 조회에 실패한다")
    void getPosts_shouldThrowWhenAdminNotFound() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(adminRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminPostService.getPosts(1L, null, null, null, null, null, pageable))
                .isInstanceOf(AdminException.class);
    }

    @Test
    @DisplayName("슈퍼 관리자가 게시글을 직접 삭제하면 게시글 강제 삭제를 위임한다")
    void deletePost_shouldForceDeletePost() {
        Post post = post();
        AdminDeletePostRequestDto requestDto = new AdminDeletePostRequestDto();
        ReflectionTestUtils.setField(requestDto, "reason", "부적절한 게시글");

        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin()));
        when(postInternalService.getPostByIdWithLock(10L)).thenReturn(post);
        when(reportInternalService.existsPendingReport(10L)).thenReturn(false);
        when(postModerationService.forceDeletePost(post, "부적절한 게시글")).thenReturn(200);

        AdminDeletePostResponseDto response = adminPostService.deletePost(1L, 10L, requestDto);

        assertThat(response.refundedPoint()).isEqualTo(200);
        verify(postModerationService).forceDeletePost(post, "부적절한 게시글");
    }

    private Admin admin() {
        return Admin.createAdmin("admin@test.com", "encoded", "관리자", AdminRole.SUPER_ADMIN);
    }

    private Post post() {
        Post post = Post.builder()
                .authorId(2L)
                .meetAt(LocalDateTime.now().plusDays(1))
                .placeName("정문")
                .placeLat(BigDecimal.valueOf(37.1))
                .placeLng(BigDecimal.valueOf(127.1))
                .content("같이 식사")
                .authorDeposit(200)
                .maxApplicants(2)
                .build();
        ReflectionTestUtils.setField(post, "id", 10L);
        return post;
    }
}
