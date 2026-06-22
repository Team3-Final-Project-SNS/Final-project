package com.example.team3final.domain.report.service;

import com.example.team3final.common.exception.ReportException;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.service.PostInternalService;
import com.example.team3final.domain.report.entity.Report;
import com.example.team3final.domain.report.enums.ReportReason;
import com.example.team3final.domain.report.enums.ReportStatus;
import com.example.team3final.domain.report.repository.ReportRepository;
import com.example.team3final.domain.user.service.UserModerationService;
import com.example.team3final.domain.user.service.UserPointService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportModerationService 단위 테스트")
class ReportModerationServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private UserPointService userPointService;

    @Mock
    private NotificationPublisher notificationPublisher;

    @Mock
    private UserModerationService userModerationService;

    @Mock
    private PostInternalService postInternalService;

    @InjectMocks
    private ReportModerationServiceImpl reportModerationService;

    @Test
    @DisplayName("대기 상태가 아닌 신고를 채택하려 하면 실패한다")
    void acceptReport_shouldThrowWhenAlreadyProcessed() {
        when(reportRepository.acceptIfPending(10L, 1L)).thenReturn(0);

        assertThatThrownBy(() -> reportModerationService.acceptReport(10L, 1L))
                .isInstanceOf(ReportException.class);
    }

    @Test
    @DisplayName("신고를 기각하면 신고자에게 기각 알림을 보낸다")
    void rejectReport_shouldNotifyReporter() {
        Report report = report();
        when(reportRepository.rejectIfPending(10L, 1L)).thenReturn(1);
        when(reportRepository.findById(10L)).thenReturn(Optional.of(report));
        when(reportRepository.countByReporterIdAndStatus(2L, ReportStatus.REJECTED)).thenReturn(1);

        reportModerationService.rejectReport(10L, 1L);

        verify(notificationPublisher).sendReportRejected(2L, 10L);
    }

    private Report report() {
        Report report = Report.builder()
                .reporterId(2L)
                .targetId(20L)
                .reason(ReportReason.SPAM)
                .detail("광고")
                .build();
        ReflectionTestUtils.setField(report, "id", 10L);
        return report;
    }
}
