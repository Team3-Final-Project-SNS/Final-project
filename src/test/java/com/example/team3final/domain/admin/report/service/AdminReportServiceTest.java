package com.example.team3final.domain.admin.report.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.AdminException;
import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.enums.AdminRole;
import com.example.team3final.domain.admin.report.dto.request.AdminProcessReportRequestDto;
import com.example.team3final.domain.admin.repository.AdminRepository;
import com.example.team3final.domain.report.entity.Report;
import com.example.team3final.domain.report.enums.ReportReason;
import com.example.team3final.domain.report.enums.ReportStatus;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("관리자 신고 서비스 단위 테스트")
class AdminReportServiceTest {

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private ReportInternalService reportInternalService;

    @Mock
    private ReportModerationService reportModerationService;

    @Mock
    private UserInternalService userInternalService;

    @InjectMocks
    private AdminReportServiceImpl adminReportService;

    @Test
    @DisplayName("관리자가 신고 목록을 조회하면 신고 내부 서비스 결과를 페이지 응답으로 반환한다")
    void getReports_shouldReturnPageResponse() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin()));
        when(reportInternalService.getReportsForAdmin(ReportStatus.PENDING, pageable)).thenReturn(Page.empty(pageable));

        PageResponseDto<?> response = adminReportService.getReports(1L, ReportStatus.PENDING, pageable);

        assertThat(response.totalElements()).isZero();
        verify(reportInternalService).getReportsForAdmin(ReportStatus.PENDING, pageable);
    }

    @Test
    @DisplayName("관리자가 없으면 신고 목록 조회에 실패한다")
    void getReports_shouldThrowWhenAdminNotFound() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(adminRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminReportService.getReports(1L, null, pageable))
                .isInstanceOf(AdminException.class);
    }

    @Test
    @DisplayName("신고를 채택 처리하면 신고 운영 서비스에 채택 처리를 위임한다")
    void processReport_shouldAcceptReport() {
        Report pendingReport = report();
        Report acceptedReport = report();
        acceptedReport.accept(1L);
        acceptedReport.markRewarded();
        AdminProcessReportRequestDto requestDto = new AdminProcessReportRequestDto();
        ReflectionTestUtils.setField(requestDto, "reportStatus", ReportStatus.ACCEPTED);

        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin()));
        when(reportInternalService.getReportById(10L)).thenReturn(pendingReport, acceptedReport);

        adminReportService.processReport(1L, 10L, requestDto);

        verify(reportModerationService).acceptReport(10L, 1L);
    }

    private Admin admin() {
        return Admin.createAdmin("admin@test.com", "encoded", "관리자", AdminRole.SUPER_ADMIN);
    }

    private Report report() {
        Report report = Report.builder()
                .reporterId(2L)
                .targetId(10L)
                .reason(ReportReason.SPAM)
                .detail("광고")
                .build();
        ReflectionTestUtils.setField(report, "id", 10L);
        return report;
    }
}
