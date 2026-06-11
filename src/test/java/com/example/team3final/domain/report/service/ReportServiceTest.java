package com.example.team3final.domain.report.service;

import com.example.team3final.domain.admin.service.AdminService;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.service.PostService;
import com.example.team3final.domain.report.enums.ReportStatus;
import com.example.team3final.domain.report.dto.request.CreateReportRequestDto;
import com.example.team3final.domain.report.dto.response.CreateReportResponseDto;
import com.example.team3final.domain.report.entity.Report;
import com.example.team3final.domain.report.enums.ReportReason;
import com.example.team3final.domain.report.repository.ReportRepository;
import com.example.team3final.domain.user.service.UserPointService;
import com.example.team3final.domain.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @InjectMocks
    private ReportServiceImpl reportService;

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

    @Test
    @DisplayName("신고 생성 - 성공")
    void createReport_Success() {
        // given
        Long reporterId = 1L;
        Long targetPostId = 100L;
        Long authorId = 2L;
        CreateReportRequestDto request = new CreateReportRequestDto(targetPostId, ReportReason.ABUSE, "DETAIL");

        Post post = mock(Post.class);
        given(post.getAuthorId()).willReturn(authorId);
        given(postService.getPostByIdWithLock(targetPostId)).willReturn(post);
        given(userService.isReportBanned(reporterId)).willReturn(false);
        given(reportRepository.existsByReporterIdAndTargetIdAndStatusIn(anyLong(), anyLong(), any())).willReturn(false);
        given(adminService.getActiveAdminIds()).willReturn(List.of(10L));

        Report report = Report.builder()
                .reporterId(reporterId)
                .targetId(targetPostId)
                .reason(ReportReason.ABUSE)
                .detail("DETAIL")
                .build();
        ReflectionTestUtils.setField(report, "id", 1L);
        given(reportRepository.save(any(Report.class))).willReturn(report);

        // when
        CreateReportResponseDto result = reportService.createReport(reporterId, request);

        // then
        assertThat(result.reportId()).isEqualTo(1L);
        verify(reportRepository).save(any(Report.class));
        verify(notificationPublisher).sendReportSubmitted(eq(10L), any());
    }

    @Test
    @DisplayName("신고 승인 - 성공")
    void acceptReport_Success() {
        Report report = createReport(1L, 1L, 100L);

        given(reportRepository.acceptIfPending(1L, 10L)).willReturn(1);
        given(reportRepository.findById(1L)).willReturn(Optional.of(report));
        given(reportRepository.countRewardedThisMonth(eq(1L), any())).willReturn(0);
        given(reportRepository.countByTargetIdAndStatus(100L, ReportStatus.ACCEPTED)).willReturn(1);

        reportService.acceptReport(1L, 10L);

        verify(reportRepository).acceptIfPending(1L, 10L);
        verify(userPointService).rewardReportPoint(1L, 50);
        verify(notificationPublisher).sendReportAcceptedPoint(1L, 1L);
    }

    @Test
    @DisplayName("신고 거절 - 성공")
    void rejectReport_Success() {
        Report report = createReport(1L, 1L, 100L);

        given(reportRepository.rejectIfPending(1L, 10L)).willReturn(1);
        given(reportRepository.findById(1L)).willReturn(Optional.of(report));
        given(reportRepository.countByReporterIdAndStatus(1L, ReportStatus.REJECTED)).willReturn(1);

        reportService.rejectReport(1L, 10L);

        verify(reportRepository).rejectIfPending(1L, 10L);
        verify(notificationPublisher).sendReportRejected(1L, 1L);
    }

    @Test
    @DisplayName("신고 엔티티 조회 - 성공")
    void getReportById_Success() {
        Report report = createReport(1L, 1L, 100L);
        given(reportRepository.findById(1L)).willReturn(Optional.of(report));

        Report result = reportService.getReportById(1L);

        assertThat(result).isSameAs(report);
    }

    @Test
    @DisplayName("관리자 신고 목록 조회 - 성공")
    void getReportsForAdmin_Success() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Report> page = new PageImpl<>(List.of(createReport(1L, 1L, 100L)));
        given(reportRepository.findAllByStatusFilter(ReportStatus.PENDING, pageable)).willReturn(page);

        Page<Report> result = reportService.getReportsForAdmin(ReportStatus.PENDING, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("대기 신고 존재 여부 조회 - 성공")
    void existsPendingReport_Success() {
        given(reportRepository.existsByTargetIdAndStatus(100L, ReportStatus.PENDING)).willReturn(true);

        boolean result = reportService.existsPendingReport(100L);

        assertThat(result).isTrue();
    }

    private Report createReport(Long id, Long reporterId, Long targetId) {
        Report report = Report.builder()
                .reporterId(reporterId)
                .targetId(targetId)
                .reason(ReportReason.ABUSE)
                .detail("DETAIL")
                .build();
        ReflectionTestUtils.setField(report, "id", id);
        return report;
    }
}
