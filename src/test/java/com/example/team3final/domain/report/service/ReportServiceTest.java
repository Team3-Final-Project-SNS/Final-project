package com.example.team3final.domain.report.service;

import com.example.team3final.domain.admin.service.AdminService;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.ReportException;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.entity.Post;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @DisplayName("신고 생성 - 본인 게시글은 신고할 수 없다")
    void createReport_SelfReport() {
        CreateReportRequestDto request = request();
        given(postService.getPostByIdWithLock(100L)).willReturn(post(1L, null));

        assertReportError(() -> reportService.createReport(1L, request), ErrorCode.REPORT_SELF_REPORT);

        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("신고 생성 - 신고 기능이 정지된 사용자는 신고할 수 없다")
    void createReport_FeatureBanned() {
        given(postService.getPostByIdWithLock(100L)).willReturn(post(2L, null));
        given(userService.isReportBanned(1L)).willReturn(true);

        assertReportError(() -> reportService.createReport(1L, request()), ErrorCode.REPORT_FEATURE_BANNED);

        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("신고 생성 - 대기 또는 채택 상태의 중복 신고는 거부한다")
    void createReport_AlreadyReported() {
        given(postService.getPostByIdWithLock(100L)).willReturn(post(2L, null));
        given(reportRepository.existsByReporterIdAndTargetIdAndStatusIn(eq(1L), eq(100L), any()))
                .willReturn(true);

        assertReportError(() -> reportService.createReport(1L, request()), ErrorCode.REPORT_ALREADY_REPORTED);

        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("신고 생성 - 최근 기각 후 게시글 수정이 없으면 3일간 재신고할 수 없다")
    void createReport_TooSoonAfterRejection() {
        LocalDateTime processedAt = LocalDateTime.now().minusDays(1);
        given(postService.getPostByIdWithLock(100L)).willReturn(post(2L, processedAt.minusHours(1)));
        given(reportRepository.findTopByReporterIdAndTargetIdAndStatusOrderByProcessedAtDesc(
                1L, 100L, ReportStatus.REJECTED))
                .willReturn(Optional.of(rejectedReport(processedAt)));

        assertReportError(() -> reportService.createReport(1L, request()), ErrorCode.REPORT_TOO_SOON);

        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("신고 생성 - 최근 기각 후 게시글이 수정됐으면 재신고를 허용한다")
    void createReport_PostUpdatedAfterRejection() {
        LocalDateTime processedAt = LocalDateTime.now().minusDays(1);
        given(postService.getPostByIdWithLock(100L)).willReturn(post(2L, processedAt.plusMinutes(1)));
        given(reportRepository.findTopByReporterIdAndTargetIdAndStatusOrderByProcessedAtDesc(
                1L, 100L, ReportStatus.REJECTED))
                .willReturn(Optional.of(rejectedReport(processedAt)));
        Report saved = createReport(2L, 1L, 100L);
        given(reportRepository.save(any(Report.class))).willReturn(saved);
        given(adminService.getActiveAdminIds()).willReturn(List.of());

        CreateReportResponseDto result = reportService.createReport(1L, request());

        assertThat(result.reportId()).isEqualTo(2L);
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
        verify(notificationPublisher).sendPostWarned(eq(100L), anyString(), anyString());
    }

    @Test
    @DisplayName("신고 승인 - 이미 처리된 신고는 다시 처리할 수 없다")
    void acceptReport_AlreadyProcessed() {
        given(reportRepository.acceptIfPending(1L, 10L)).willReturn(0);

        assertReportError(() -> reportService.acceptReport(1L, 10L), ErrorCode.REPORT_ALREADY_PROCESSED);

        verifyNoInteractions(userPointService, notificationPublisher);
    }

    @Test
    @DisplayName("신고 승인 - 조건부 갱신 후 신고가 없으면 실패한다")
    void acceptReport_NotFound() {
        given(reportRepository.acceptIfPending(1L, 10L)).willReturn(1);
        given(reportRepository.findById(1L)).willReturn(Optional.empty());

        assertReportError(() -> reportService.acceptReport(1L, 10L), ErrorCode.REPORT_NOT_FOUND);
    }

    @Test
    @DisplayName("신고 승인 - 월 포상 한도에 도달하면 포인트를 지급하지 않는다")
    void acceptReport_MonthlyRewardLimit() {
        Report report = createReport(1L, 1L, 100L);
        given(reportRepository.acceptIfPending(1L, 10L)).willReturn(1);
        given(reportRepository.findById(1L)).willReturn(Optional.of(report));
        given(reportRepository.countRewardedThisMonth(eq(1L), any())).willReturn(6);
        given(reportRepository.countByTargetIdAndStatus(100L, ReportStatus.ACCEPTED)).willReturn(1);

        reportService.acceptReport(1L, 10L);

        verify(userPointService, never()).rewardReportPoint(anyLong(), anyInt());
        assertThat(report.isRewarded()).isFalse();
        verify(notificationPublisher).sendReportAcceptedPoint(1L, 1L);
    }

    @ParameterizedTest(name = "채택 누적 {0}회면 {1}일 정지")
    @CsvSource({"3,3", "4,10", "5,30"})
    @DisplayName("신고 승인 - 누적 횟수에 따라 신고 대상 ID를 기간 정지 처리한다")
    void acceptReport_SuspendsReportedTarget(int acceptedCount, int suspensionDays) {
        Report report = createReport(1L, 1L, 100L);
        given(reportRepository.acceptIfPending(1L, 10L)).willReturn(1);
        given(reportRepository.findById(1L)).willReturn(Optional.of(report));
        given(reportRepository.countByTargetIdAndStatus(100L, ReportStatus.ACCEPTED))
                .willReturn(acceptedCount);

        reportService.acceptReport(1L, 10L);

        verify(userService).suspendUser(100L, suspensionDays);
        verify(notificationPublisher).sendAccountSuspended(eq(100L), anyString(), anyString());
    }

    @Test
    @DisplayName("신고 승인 - 누적 6회 이상이면 신고 대상 ID를 영구 정지 처리한다")
    void acceptReport_PermanentlySuspendsReportedTarget() {
        Report report = createReport(1L, 1L, 100L);
        given(reportRepository.acceptIfPending(1L, 10L)).willReturn(1);
        given(reportRepository.findById(1L)).willReturn(Optional.of(report));
        given(reportRepository.countByTargetIdAndStatus(100L, ReportStatus.ACCEPTED)).willReturn(6);

        reportService.acceptReport(1L, 10L);

        verify(userService).suspendUser(100L, null);
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
    @DisplayName("신고 거절 - 이미 처리된 신고는 다시 처리할 수 없다")
    void rejectReport_AlreadyProcessed() {
        given(reportRepository.rejectIfPending(1L, 10L)).willReturn(0);

        assertReportError(() -> reportService.rejectReport(1L, 10L), ErrorCode.REPORT_ALREADY_PROCESSED);

        verifyNoInteractions(userService, notificationPublisher);
    }

    @Test
    @DisplayName("신고 거절 - 조건부 갱신 후 신고가 없으면 실패한다")
    void rejectReport_NotFound() {
        given(reportRepository.rejectIfPending(1L, 10L)).willReturn(1);
        given(reportRepository.findById(1L)).willReturn(Optional.empty());

        assertReportError(() -> reportService.rejectReport(1L, 10L), ErrorCode.REPORT_NOT_FOUND);
    }

    @Test
    @DisplayName("신고 거절 - 기각 횟수가 3의 배수면 신고 기능을 10일 정지한다")
    void rejectReport_BansFeatureAtThreshold() {
        Report report = createReport(1L, 1L, 100L);
        given(reportRepository.rejectIfPending(1L, 10L)).willReturn(1);
        given(reportRepository.findById(1L)).willReturn(Optional.of(report));
        given(reportRepository.countByReporterIdAndStatus(1L, ReportStatus.REJECTED)).willReturn(3);

        reportService.rejectReport(1L, 10L);

        verify(userService).banReportFeature(1L, 10);
    }

    @Test
    @DisplayName("신고 거절 - 기각 횟수가 3의 배수가 아니면 신고 기능을 정지하지 않는다")
    void rejectReport_DoesNotBanOutsideThreshold() {
        Report report = createReport(1L, 1L, 100L);
        given(reportRepository.rejectIfPending(1L, 10L)).willReturn(1);
        given(reportRepository.findById(1L)).willReturn(Optional.of(report));
        given(reportRepository.countByReporterIdAndStatus(1L, ReportStatus.REJECTED)).willReturn(2);

        reportService.rejectReport(1L, 10L);

        verify(userService, never()).banReportFeature(anyLong(), anyInt());
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
    @DisplayName("신고 엔티티 조회 - 신고가 없으면 실패")
    void getReportById_NotFound() {
        given(reportRepository.findById(1L)).willReturn(Optional.empty());

        assertReportError(() -> reportService.getReportById(1L), ErrorCode.REPORT_NOT_FOUND);
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

    private CreateReportRequestDto request() {
        return new CreateReportRequestDto(100L, ReportReason.ABUSE, "DETAIL");
    }

    private Post post(Long authorId, LocalDateTime updatedAt) {
        Post post = Post.builder()
                .authorId(authorId)
                .build();
        if (updatedAt != null) {
            ReflectionTestUtils.setField(post, "updatedAt", updatedAt);
        }
        return post;
    }

    private Report rejectedReport(LocalDateTime processedAt) {
        Report report = createReport(1L, 1L, 100L);
        ReflectionTestUtils.setField(report, "status", ReportStatus.REJECTED);
        ReflectionTestUtils.setField(report, "processedAt", processedAt);
        return report;
    }

    private void assertReportError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ReportException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }
}
