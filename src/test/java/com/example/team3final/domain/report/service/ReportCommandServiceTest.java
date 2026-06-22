package com.example.team3final.domain.report.service;

import com.example.team3final.common.exception.ReportException;
import com.example.team3final.domain.admin.service.AdminService;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.service.PostInternalService;
import com.example.team3final.domain.report.dto.request.CreateReportRequestDto;
import com.example.team3final.domain.report.dto.response.CreateReportResponseDto;
import com.example.team3final.domain.report.entity.Report;
import com.example.team3final.domain.report.enums.ReportReason;
import com.example.team3final.domain.report.enums.ReportStatus;
import com.example.team3final.domain.report.repository.ReportRepository;
import com.example.team3final.domain.user.service.UserModerationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportCommandService 단위 테스트")
class ReportCommandServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private PostInternalService postInternalService;

    @Mock
    private UserModerationService userModerationService;

    @Mock
    private AdminService adminService;

    @Mock
    private NotificationPublisher notificationPublisher;

    @InjectMocks
    private ReportCommandServiceImpl reportCommandService;

    @Test
    @DisplayName("신고 생성은 게시글 잠금 조회, 중복 검증, 저장, 관리자 알림을 수행한다")
    void createReport_shouldSaveReportAndNotifyAdmins() {
        Post post = post(2L);
        CreateReportRequestDto request = CreateReportRequestDto.builder()
                .targetId(10L)
                .reason(ReportReason.OTHER)
                .detail("신고 상세")
                .build();
        Report savedReport = report(100L, 1L, 10L, ReportStatus.PENDING);
        when(postInternalService.getPostByIdWithLock(10L)).thenReturn(post);
        when(userModerationService.isReportBanned(1L)).thenReturn(false);
        when(reportRepository.existsByReporterIdAndTargetIdAndStatusIn(any(), any(), any())).thenReturn(false);
        when(reportRepository.findTopByReporterIdAndTargetIdAndStatusOrderByProcessedAtDesc(1L, 10L, ReportStatus.REJECTED))
                .thenReturn(Optional.empty());
        when(reportRepository.save(any(Report.class))).thenReturn(savedReport);
        when(adminService.getActiveAdminIds()).thenReturn(List.of(1000L));

        CreateReportResponseDto result = reportCommandService.createReport(1L, request);

        assertThat(result.reportId()).isEqualTo(100L);
        verify(notificationPublisher).sendReportSubmitted(1000L, 100L);
    }

    @Test
    @DisplayName("신고 생성은 본인 게시글 신고이면 신고 예외를 던진다")
    void createReport_shouldThrowWhenSelfReport() {
        CreateReportRequestDto request = CreateReportRequestDto.builder()
                .targetId(10L)
                .reason(ReportReason.OTHER)
                .detail("신고 상세")
                .build();
        when(postInternalService.getPostByIdWithLock(10L)).thenReturn(post(1L));

        assertThatThrownBy(() -> reportCommandService.createReport(1L, request))
                .isInstanceOf(ReportException.class);
    }

    @Test
    @DisplayName("신고 생성은 이미 처리 중인 신고가 있으면 신고 예외를 던진다")
    void createReport_shouldThrowWhenAlreadyReported() {
        CreateReportRequestDto request = CreateReportRequestDto.builder()
                .targetId(10L)
                .reason(ReportReason.OTHER)
                .detail("신고 상세")
                .build();
        when(postInternalService.getPostByIdWithLock(10L)).thenReturn(post(2L));
        when(userModerationService.isReportBanned(1L)).thenReturn(false);
        when(reportRepository.existsByReporterIdAndTargetIdAndStatusIn(any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> reportCommandService.createReport(1L, request))
                .isInstanceOf(ReportException.class);
    }

    private Post post(Long authorId) {
        return Post.builder()
                .authorId(authorId)
                .meetAt(LocalDateTime.of(2026, 1, 1, 12, 0))
                .placeName("테스트 장소")
                .placeLat(new BigDecimal("37.5665"))
                .placeLng(new BigDecimal("126.9780"))
                .content("본문")
                .authorDeposit(200)
                .maxApplicants(2)
                .build();
    }

    private Report report(Long reportId, Long reporterId, Long targetId, ReportStatus status) {
        Report report = Report.builder()
                .reporterId(reporterId)
                .targetId(targetId)
                .reason(ReportReason.OTHER)
                .detail("신고 상세")
                .build();
        ReflectionTestUtils.setField(report, "id", reportId);
        ReflectionTestUtils.setField(report, "status", status);
        return report;
    }
}
