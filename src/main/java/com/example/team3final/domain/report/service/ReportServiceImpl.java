package com.example.team3final.domain.report.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.ReportException;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.service.PostService;
import com.example.team3final.domain.report.dto.request.CreateReportRequestDto;
import com.example.team3final.domain.report.dto.response.CreateReportResponseDto;
import com.example.team3final.domain.report.dto.response.DeleteReportResponseDto;
import com.example.team3final.domain.report.dto.response.GetMyReportsResponseDto;
import com.example.team3final.domain.report.entity.Report;
import com.example.team3final.domain.report.enums.ReportStatus;
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
    private final PostService postService;

    // 신고 접수
    @Override
    @Transactional
    public CreateReportResponseDto createReport(Long reporterId, CreateReportRequestDto request) {

        // 신고 대상 게시글 존재 여부 확인 + 본인 게시글 신고 차단
        Post post = postService.getPostById(request.getTargetId());
        if (post.getAuthorId().equals(reporterId)) {
            throw new ReportException(ErrorCode.REPORT_SELF_REPORT);
        }

        // 중복 신고 방지
        if (reportRepository.existsByReporterIdAndTargetId(
                reporterId, request.getTargetId())) {
            throw new ReportException(ErrorCode.REPORT_ALREADY_REPORTED);
        }

        // 기각된 신고에 대해 3일 이내 재신고 제한
        // TODO: 수정 게시글은 재신고 가능 - post.getUpdatedAt()과 기각 시각 비교 필요
        if (reportRepository.existsByReporterIdAndTargetIdAndStatusAndCreatedAtAfter(
                reporterId, request.getTargetId(),
                ReportStatus.REJECTED,
                LocalDateTime.now().minusDays(3))) {
            throw new ReportException(ErrorCode.REPORT_TOO_SOON);
        }

        // 신고 저장
        Report report = Report.builder()
                .reporterId(reporterId)
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

    // 신고 취소
    @Override
    @Transactional
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

    // 신고 채택 - 관리자 호출용
    @Override
    @Transactional
    public void acceptReport(Long reportId, Long adminId) {

        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ReportException(ErrorCode.REPORT_NOT_FOUND));

        // 이미 처리된 신고 확인
        if (report.isProcessed()) {
            throw new ReportException(ErrorCode.REPORT_ALREADY_PROCESSED);
        }

        // 채택 처리
        report.accept(adminId);

        // 포상 지급 완료 처리
        report.markRewarded();

        // 피신고자 채택 누적 횟수 조회 (제재 정책용)
        int acceptedCount = reportRepository.countByTargetIdAndStatus(
                report.getTargetId(), ReportStatus.ACCEPTED);

        // TODO: 신고자에게 포상 50P 지급 (류 담당 UserPointService 준비 후 연동)
        // userPointService.rewardPoint(report.getReporterId(), 50, reportId);

        // TODO: 채택 횟수에 따른 피신고자 제재 처리
        // userService.suspendUser(userId, days) 시그니처 확정 후 연동 (정 담당자 협의 필요)
        // 1회 → 경고, 2회 → 경고, 3회 → 3일 정지
        // 4회 → 10일 정지, 5회 → 30일 정지, 6회 이상 → 영구 정지
        // userService.applyPenalty(report.getTargetId(), acceptedCount);

        // TODO: 신고자/피신고자 알림 발송 (NotificationPublisher 구현체 완성 후 연동)
        // notificationPublisher.sendReportResult(report.getReporterId(), reportId);
        // notificationPublisher.sendReportResult(report.getTargetId(), reportId);
    }

    // 신고 기각 - 관리자 호출용
    @Override
    @Transactional
    public void rejectReport(Long reportId, Long adminId) {

        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ReportException(ErrorCode.REPORT_NOT_FOUND));

        // 이미 처리된 신고 확인
        if (report.isProcessed()) {
            throw new ReportException(ErrorCode.REPORT_ALREADY_PROCESSED);
        }

        // 기각 처리
        report.reject(adminId);

        // TODO: 신고자 알림 발송
        // notificationPublisher.sendReportResult(report.getReporterId(), reportId);
    }

    @Override
    public Report getReportById(Long reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new ReportException(ErrorCode.REPORT_NOT_FOUND));
    }

    @Override
    public Page<Report> getReportsForAdmin(ReportStatus status, Pageable pageable) {
        return reportRepository.findAllByStatusFilter(status, pageable);
    }
}