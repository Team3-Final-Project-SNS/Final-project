package com.example.team3final.domain.report.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.ReportException;
import com.example.team3final.domain.report.dto.request.CreateReportRequestDto;
import com.example.team3final.domain.report.dto.response.CreateReportResponseDto;
import com.example.team3final.domain.report.dto.response.DeleteReportResponseDto;
import com.example.team3final.domain.report.dto.response.GetMyReportsResponseDto;
import com.example.team3final.domain.report.entity.Report;
import com.example.team3final.domain.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;

    // 신고 접수
    @Transactional
    @Override
    public CreateReportResponseDto createReport(Long reporterId, CreateReportRequestDto request) {

        // 본인 신고 차단 (USER 타입만)
        if (request.getTargetType().name().equals("USER") && request.getTargetId().equals(reporterId)) {
            throw new ReportException(ErrorCode.REPORT_SELF_REPORT);
        }

        // 중복 신고 방지
        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetId(
                reporterId, request.getTargetType(), request.getTargetId())) {
            throw new ReportException(ErrorCode.REPORT_ALREADY_REPORTED);
        }

        // 10일 이내 동일 대상 재신고 제한
        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetIdAndCreatedAtAfter(
                reporterId, request.getTargetType(), request.getTargetId(),
                LocalDateTime.now().minusDays(10))) {
            throw new ReportException(ErrorCode.REPORT_TOO_SOON);
        }

        // 신고 저장
        Report report = Report.builder()
                .reporterId(reporterId)
                .targetType(request.getTargetType())
                .targetId(request.getTargetId())
                .reason(request.getReason())
                .detail(request.getDetail())
                .build();

        return CreateReportResponseDto.from(reportRepository.save(report));
    }

    // 내 신고 내역 조회
    @Override
    public PageResponseDto<GetMyReportsResponseDto> getMyReports(Long reporterId, Pageable pageable) {

        Page<GetMyReportsResponseDto> page = reportRepository
                .findByReporterIdOrderByCreatedAtDesc(reporterId, pageable)
                .map(GetMyReportsResponseDto::from);

        return PageResponseDto.from(page);
    }

    // 신고 취소 (소프트 딜리트 - WITHDRAWN 상태로 변경)
    @Transactional
    @Override
    public DeleteReportResponseDto deleteReport(Long reporterId, Long reportId) {

        // 신고 존재 여부 + 본인 신고 확인
        Report report = reportRepository.findByIdAndReporterId(reportId, reporterId)
                .orElseThrow(() -> new ReportException(ErrorCode.REPORT_NOT_FOUND));

        // 이미 처리된 신고는 취소 불가
        if (report.isProcessed()) {
            throw new ReportException(ErrorCode.REPORT_ALREADY_PROCESSED);
        }

        // 소프트 딜리트 (WITHDRAWN)
        report.withdraw();

        return DeleteReportResponseDto.of(report.getId(), report.getCancelledAt());
    }
}