package com.example.team3final.domain.report.service;

import com.example.team3final.common.exception.ReportException;
import com.example.team3final.domain.report.entity.Report;
import com.example.team3final.domain.report.enums.ReportReason;
import com.example.team3final.domain.report.enums.ReportStatus;
import com.example.team3final.domain.report.repository.ReportRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportInternalService 단위 테스트")
class ReportInternalServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @InjectMocks
    private ReportInternalServiceImpl reportInternalService;

    @Test
    @DisplayName("신고 ID로 내부 조회하면 신고를 반환한다")
    void getReportById_shouldReturnReport() {
        Report report = report();
        when(reportRepository.findById(10L)).thenReturn(Optional.of(report));

        Report response = reportInternalService.getReportById(10L);

        assertThat(response).isSameAs(report);
    }

    @Test
    @DisplayName("신고가 없으면 내부 조회에 실패한다")
    void getReportById_shouldThrowWhenReportNotFound() {
        when(reportRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reportInternalService.getReportById(10L))
                .isInstanceOf(ReportException.class);
    }

    @Test
    @DisplayName("관리자 신고 목록 조회는 상태 필터를 저장소에 위임한다")
    void getReportsForAdmin_shouldDelegateWithStatusFilter() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(reportRepository.findAllByStatusFilter(ReportStatus.PENDING, pageable)).thenReturn(Page.empty(pageable));

        reportInternalService.getReportsForAdmin(ReportStatus.PENDING, pageable);

        verify(reportRepository).findAllByStatusFilter(ReportStatus.PENDING, pageable);
    }

    private Report report() {
        Report report = Report.builder()
                .reporterId(1L)
                .targetId(10L)
                .reason(ReportReason.SPAM)
                .detail("광고")
                .build();
        ReflectionTestUtils.setField(report, "id", 10L);
        return report;
    }
}
