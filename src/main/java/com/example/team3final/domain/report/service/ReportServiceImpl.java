package com.example.team3final.domain.report.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.ReportException;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.service.PostService;
import com.example.team3final.domain.report.dto.request.CreateReportRequestDto;
import com.example.team3final.domain.report.dto.response.CreateReportResponseDto;
import com.example.team3final.domain.report.dto.response.DeleteReportResponseDto;
import com.example.team3final.domain.report.dto.response.GetMyReportsResponseDto;
import com.example.team3final.domain.report.entity.Report;
import com.example.team3final.domain.report.enums.ReportStatus;
import com.example.team3final.domain.report.repository.ReportRepository;
import com.example.team3final.domain.user.service.UserPointService;
import com.example.team3final.domain.user.service.UserService;
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
    private final UserPointService userPointService;
    private final UserService userService;
    private final NotificationPublisher notificationPublisher;

    // 포상 지급 포인트
    private static final int REPORT_REWARD_POINT = 50;

    // 제재 정책 - 채택 누적 횟수에 따른 정지 일수
    // 1~2회: 경고 (정지 없음)
    // 3회: 3일 정지
    // 4회: 10일 정지
    // 5회: 30일 정지
    // 6회 이상: 영구정지 (null)
    private static final int SUSPEND_WARNING_THRESHOLD = 2;   // 이하: 경고
    private static final int SUSPEND_3DAY_THRESHOLD = 3;      // 3일 정지
    private static final int SUSPEND_10DAY_THRESHOLD = 4;     // 10일 정지
    private static final int SUSPEND_30DAY_THRESHOLD = 5;     // 30일 정지

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
        // 단, 게시글이 기각 이후에 수정됐으면 재신고 허용
        reportRepository.findByReporterIdAndTargetIdAndStatus(reporterId, request.getTargetId(), ReportStatus.REJECTED)
                .ifPresent(rejectReport -> {

                    // 기각 시각이 3일 이내인지 확인
                    boolean isWithin3Days = rejectReport.getProcessedAt()
                            .isAfter(LocalDateTime.now().minusDays(3));

                    // 게시글이 기각 이후에 수정됐는지 확인
                    boolean isPostUpdatedAfterRejection = post.getUpdatedAt() != null && post.getUpdatedAt()
                            .isAfter(rejectReport.getProcessedAt());

                    // 3일 이내이고 게시글 수정도 없으면 재신고 차단
                    if (isWithin3Days && !isPostUpdatedAfterRejection) {
                        throw new ReportException(ErrorCode.REPORT_TOO_SOON);
                    }
                });

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

        userPointService.rewardReportPoint(report.getReporterId(), REPORT_REWARD_POINT);

        // 채택 횟수에 따른 피신고자 제재 처리
        // 1. acceptedCount가 2보다 크면 (3회 이상이면) 제재 시작
        if (acceptedCount > SUSPEND_WARNING_THRESHOLD) {

            // 2. 정지 일수 담을 변수 선언 (아직 값 없음)
            Integer days;

            // 3. 횟수에 따라 days 값 결정
            if (acceptedCount == SUSPEND_3DAY_THRESHOLD) {
                days = 3;       // 3회 → 3일 정지
            } else if (acceptedCount == SUSPEND_10DAY_THRESHOLD) {
                days = 10;      // 4회 → 10일 정지
            } else if (acceptedCount == SUSPEND_30DAY_THRESHOLD) {
                days = 30;      // 5회 → 30일 정지
            } else {
                days = null;    // 6회 이상 → 영구정지
            }

            // 4. days 값으로 제재 처리
            userService.suspendUser(report.getTargetId(), days);
        }

        // 신고자에게 채택 알림 + 포상 포인트 알림
        notificationPublisher.sendReportResult(report.getReporterId(), reportId);
        notificationPublisher.sendReportAcceptedPoint(report.getReporterId(), reportId);

        // 피신고자에게 신고 처리 결과 알림
        notificationPublisher.sendReportResult(report.getTargetId(), reportId);
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

        // 신고자에게 기각 알림
        notificationPublisher.sendReportResult(report.getReporterId(), reportId);
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
