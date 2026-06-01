package com.example.team3final.domain.admin.report.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.AdminException;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.domain.admin.report.dto.request.AdminProcessReportRequestDto;
import com.example.team3final.domain.admin.report.dto.response.AdminGetReportResponseDto;
import com.example.team3final.domain.admin.report.dto.response.AdminProcessReportResponseDto;
import com.example.team3final.domain.admin.repository.AdminRepository;
import com.example.team3final.domain.report.entity.Report;
import com.example.team3final.domain.report.enums.ReportStatus;
import com.example.team3final.domain.report.service.ReportService;
import com.example.team3final.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReportServiceImpl implements AdminReportService {

    private final AdminRepository adminRepository;
    private final ReportService reportService;
    private final UserService userService;

    // 포상 포인트 상수
    private static final int REWARD_POINT = 50;
    private static final int NO_REWARD_POINT = 0;


    // 신고 목록 조회
    @Override
    public PageResponseDto<AdminGetReportResponseDto> getReports(Long adminId, ReportStatus status, Pageable pageable) {

        // 관리자 존재 여부 확인
        adminRepository.findById(adminId)
                .orElseThrow(() -> new AdminException(ErrorCode.ADMIN_NOT_FOUND));

        // 신고 목록 조회
        Page<Report> reportPage = reportService.getReportsForAdmin(status, pageable);

        // 신고 ID 목록 추출
        List<Long> reporterIds = reportPage.getContent()
                .stream()
                .map(Report::getReporterId)
                .distinct()
                .toList();

        // UserService 배치 조회 -> N+1 방지
        Map<Long, String> nicknameMap = userService.getUserNicknameMap(reporterIds);

        // Report 엔티티 DTO 변환
        Page<AdminGetReportResponseDto> dtoPage = reportPage.map(report -> {
            String reporterNickname = nicknameMap.getOrDefault(report.getReporterId(), "탈퇴한 유저");
            return AdminGetReportResponseDto.of(report, reporterNickname);
        });

        return PageResponseDto.from(dtoPage);
    }

    // 신고 접수
    @Override
    @Transactional
    public AdminProcessReportResponseDto processReport(
            Long adminId,
            Long reportId,
            AdminProcessReportRequestDto requestDto) {

        // 관리자 존재 여부 확인
        adminRepository.findById(adminId)
                .orElseThrow(() -> new AdminException(ErrorCode.ADMIN_NOT_FOUND));

        // 신고 단건 조회
        Report report = reportService.getReportById(reportId);

        // 이미 처리된 신고인지 확인
        if (report.isProcessed()) {
            throw new AdminException(ErrorCode.REPORT_ALREADY_PROCESSED);
        }

        // 요청 status에 따라 ACCEPTED(채택) / REJECTED(기각) 분기
        switch (requestDto.getReportStatus()) {
            case ACCEPTED -> {
                reportService.acceptReport(reportId, adminId);
            }
            case REJECTED -> {
                reportService.rejectReport(reportId, adminId);
            }
            default -> {
                throw new AdminException(ErrorCode.ADMIN_INVALID_REPORT_STATUS);
            }
        }

        // case 문에서 처리된 결과를 다시 조회
        Report processedReport = reportService.getReportById(reportId);

        // ACCEPTED면 50포인트, REJECTED면 0포인트 지급
        int rewardPoint = processedReport.isRewarded() ? REWARD_POINT : NO_REWARD_POINT;

        return AdminProcessReportResponseDto.of(processedReport, rewardPoint);
    }
}

