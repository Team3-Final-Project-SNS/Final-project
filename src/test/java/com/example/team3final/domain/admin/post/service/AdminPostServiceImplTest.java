package com.example.team3final.domain.admin.post.service;

import com.example.team3final.common.exception.AdminException;
import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.post.dto.request.AdminDeletePostRequestDto;
import com.example.team3final.domain.admin.post.dto.response.AdminDeletePostResponseDto;
import com.example.team3final.domain.admin.repository.AdminRepository;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.service.PostService;
import com.example.team3final.domain.report.entity.Report;
import com.example.team3final.domain.report.enums.ReportStatus;
import com.example.team3final.domain.report.service.ReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AdminPostServiceImplTest {

    @Mock
    private AdminRepository adminRepository;
    @Mock
    private PostService postService;
    @Mock
    private ReportService reportService;

    @InjectMocks
    private AdminPostServiceImpl adminPostService;

    @Test
    @DisplayName("게시글 강제 삭제 - 성공")
    void deletePost_Success() {
        // given
        Long adminId = 1L;
        Long postId = 100L;
        Long reportId = 50L;
        
        Admin admin = mock(Admin.class);
        given(admin.isActiveAndSuperAdmin()).willReturn(true);
        given(adminRepository.findById(adminId)).willReturn(Optional.of(admin));
        
        Post post = mock(Post.class);
        given(post.isOpen()).willReturn(true);
        given(postService.getPostById(postId)).willReturn(post);
        
        Report report = mock(Report.class);
        given(report.getTargetId()).willReturn(postId);
        given(report.getStatus()).willReturn(ReportStatus.ACCEPTED);
        given(reportService.getReportById(reportId)).willReturn(report);
        
        given(postService.forceDeletePost(post)).willReturn(500);
        
        AdminDeletePostRequestDto request = new AdminDeletePostRequestDto();
        ReflectionTestUtils.setField(request, "reportId", reportId);
        ReflectionTestUtils.setField(request, "reason", "Policy violation");

        // when
        AdminDeletePostResponseDto result = adminPostService.deletePost(adminId, postId, request);

        // then
        assertNotNull(result);
    }
}
