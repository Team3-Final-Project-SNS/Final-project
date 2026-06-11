package com.example.team3final.domain.admin.report.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.report.dto.request.AdminProcessReportRequestDto;
import com.example.team3final.domain.admin.report.dto.response.AdminGetReportsResponseDto;
import com.example.team3final.domain.admin.report.dto.response.AdminProcessReportResponseDto;
import com.example.team3final.domain.admin.repository.AdminRepository;
import com.example.team3final.domain.report.entity.Report;
import com.example.team3final.domain.report.enums.ReportReason;
import com.example.team3final.domain.report.enums.ReportStatus;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AdminReportServiceTest {

    @InjectMocks
    private AdminReportServiceImpl adminReportService;

    @Mock
    private AdminRepository adminRepository;
    @Mock
    private ReportService reportService;
    @Mock
    private UserService userService;

    @Test
    @DisplayName("관리자 신고 목록 조회 - 성공 (빈 목록)")
    void getReports_Empty_Success() {
        // given
        Long adminId = 1L;
        Pageable pageable = PageRequest.of(0, 10);
        given(adminRepository.findById(adminId)).willReturn(Optional.of(mock(Admin.class)));

        Report report = Report.builder().reporterId(10L).targetId(100L).build();
        given(reportService.getReportsForAdmin(any(), any())).willReturn(new PageImpl<>(List.of(report)));
        given(userService.getUserNicknameMap(any())).willReturn(Map.of(10L, "nickname"));

        // when
        PageResponseDto<AdminGetReportsResponseDto> result = adminReportService.getReports(adminId, null, pageable);

        // then
        assertThat(result.content()).hasSize(1);
    }

    @Test
    @DisplayName("관리자 신고 처리 - 성공")
    void processReport_Success() {
        Report pendingReport = createReport(1L, 10L, 100L);
        Report acceptedReport = createReport(1L, 10L, 100L);
        acceptedReport.accept(1L);
        acceptedReport.markRewarded();
        AdminProcessReportRequestDto request = new AdminProcessReportRequestDto();
        ReflectionTestUtils.setField(request, "reportStatus", ReportStatus.ACCEPTED);
        given(adminRepository.findById(1L)).willReturn(Optional.of(mock(Admin.class)));
        given(reportService.getReportById(1L)).willReturn(pendingReport, acceptedReport);

        AdminProcessReportResponseDto result = adminReportService.processReport(1L, 1L, request);

        assertThat(result.reportId()).isEqualTo(1L);
        assertThat(result.rewardPoint()).isEqualTo(50);
    }

    private Report createReport(Long id, Long reporterId, Long targetId) {
        Report report = Report.builder()
                .reporterId(reporterId)
                .targetId(targetId)
                .reason(ReportReason.ABUSE)
                .detail("detail")
                .build();
        ReflectionTestUtils.setField(report, "id", id);
        return report;
    }
}
