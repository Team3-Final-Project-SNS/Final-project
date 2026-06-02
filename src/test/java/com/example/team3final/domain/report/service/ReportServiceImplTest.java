package com.example.team3final.domain.report.service;

import com.example.team3final.domain.admin.service.AdminService;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.service.PostService;
import com.example.team3final.domain.report.dto.request.CreateReportRequestDto;
import com.example.team3final.domain.report.dto.response.CreateReportResponseDto;
import com.example.team3final.domain.report.entity.Report;
import com.example.team3final.domain.report.enums.ReportReason;
import com.example.team3final.domain.report.enums.ReportStatus;
import com.example.team3final.domain.report.repository.ReportRepository;
import com.example.team3final.domain.user.service.UserPointService;
import com.example.team3final.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private PostService postService;
    @Mock
    private UserPointService userPointService;
    @Mock
    private UserService userService;
    @Mock
    private NotificationPublisher notificationPublisher;
    @Mock
    private AdminService adminService;

    @InjectMocks
    private ReportServiceImpl reportService;

    @Test
    @DisplayName("신고 접수 - 성공")
    void createReport_Success() {
        // given
        Post post = mock(Post.class);
        given(post.getAuthorId()).willReturn(200L);
        given(postService.getPostById(10L)).willReturn(post);

        given(reportRepository.existsByReporterIdAndTargetId(100L, 10L)).willReturn(false);
        given(reportRepository.findByReporterIdAndTargetIdAndStatus(anyLong(), anyLong(), any())).willReturn(Optional.empty());

        Report report = mock(Report.class);
        given(report.getId()).willReturn(1L);
        given(report.getTargetId()).willReturn(10L);
        given(report.getStatus()).willReturn(ReportStatus.PENDING);
        given(report.getCreatedAt()).willReturn(LocalDateTime.now());
        given(reportRepository.save(any(Report.class))).willReturn(report);
        given(adminService.getAdminId()).willReturn(999L);

        CreateReportRequestDto request = CreateReportRequestDto.builder()
                .targetId(10L)
                .reason(ReportReason.ABUSE)
                .detail("Detail")
                .build();

        // when
        CreateReportResponseDto result = reportService.createReport(100L, request);

        // then
        assertNotNull(result);
        verify(reportRepository).save(any(Report.class));
        verify(notificationPublisher).sendReportSubmitted(999L, 1L);
    }
}
