package com.example.team3final.domain.admin.report.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.domain.admin.entity.Admin;
import com.example.team3final.domain.admin.report.dto.request.AdminProcessReportRequestDto;
import com.example.team3final.domain.admin.report.dto.response.AdminProcessReportResponseDto;
import com.example.team3final.domain.admin.repository.AdminRepository;
import com.example.team3final.domain.report.entity.Report;
import com.example.team3final.domain.report.enums.ReportStatus;
import com.example.team3final.domain.report.service.ReportService;
import com.example.team3final.domain.user.service.UserService;
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
class AdminReportServiceImplTest {

    @Mock
    private AdminRepository adminRepository;
    @Mock
    private ReportService reportService;
    @Mock
    private UserService userService;

    @InjectMocks
    private AdminReportServiceImpl adminReportService;

    @Test
    @DisplayName("신고 접수 - 성공 (채택)")
    void processReport_Success_Accepted() {
        // given
        Long adminId = 1L;
        Long reportId = 100L;
        Admin admin = mock(Admin.class);
        given(adminRepository.findById(adminId)).willReturn(Optional.of(admin));
        
        Report report = mock(Report.class);
        given(report.isProcessed()).willReturn(false);
        given(report.isRewarded()).willReturn(true);
        given(reportService.getReportById(reportId)).willReturn(report);

        AdminProcessReportRequestDto request = new AdminProcessReportRequestDto();
        ReflectionTestUtils.setField(request, "reportStatus", ReportStatus.ACCEPTED);

        // when
        AdminProcessReportResponseDto result = adminReportService.processReport(adminId, reportId, request);

        // then
        assertNotNull(result);
    }
}
