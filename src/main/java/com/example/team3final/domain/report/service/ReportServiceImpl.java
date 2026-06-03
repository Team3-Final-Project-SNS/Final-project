package com.example.team3final.domain.report.service;

import com.example.team3final.common.dto.response.PageResponseDto;
import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.ReportException;
import com.example.team3final.domain.admin.service.AdminService;
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
import com.example.team3final.domain.user.dto.response.UserInfoDto;
import com.example.team3final.domain.user.entity.User;
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
    private final AdminService adminService;

    // 포상 지급 포인트
    private static final int REPORT_REWARD_POINT = 50;
    // 기각 3번 초과에 확인용
    private static final int REPORT_BAN_THRESHOLD = 3;
    // 10일 박탈에 활용
    private static final int REPORT_BAN_DAYS = 10;
    // 월 포상 상한
    private static final int MONTHLY_REWARD_LIMIT = 300;

    // 신고 접수
    @Override
    @Transactional
    public CreateReportResponseDto createReport(Long reporterId, CreateReportRequestDto request) {

        // 신고 대상 게시글 존재 여부 확인 + 본인 게시글 신고 차단
        Post post = postService.getPostById(request.getTargetId());
        if (post.getAuthorId().equals(reporterId)) {
            throw new ReportException(ErrorCode.REPORT_SELF_REPORT);
        }

        if (userService.isReportBanned(reporterId)) {
            throw new ReportException(ErrorCode.REPORT_FEATURE_BANNED);
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

        Report savedReport = reportRepository.save(report);

        // 25번 알림 - 관리자에게 신고 접수 알림 발송
        // adminId가 null이면 활성 관리자 없음 → 알림 스킵
        Long adminId = adminService.getAdminId();
        if (adminId != null) {
            notificationPublisher.sendReportSubmitted(adminId, savedReport.getId());
        }

        return CreateReportResponseDto.from(savedReport);
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

        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1)
                // 이번 달 1일 00:00:00
                .withHour(0).withMinute(0).withSecond(0).withNano(0);

        // 이번 달 포상 지급 횟수 조회
        int rewardedCountThisMonth = reportRepository.countRewardedThisMonth(report.getReporterId(), startOfMonth);

        // 이번 달 지급 총액 = 횟수 * 50P
        int rewardedThisMonth = rewardedCountThisMonth * REPORT_REWARD_POINT;

        // 월별 상한 (300P) 미만일 때만 포상 지급
        if (rewardedThisMonth < MONTHLY_REWARD_LIMIT) {
            userPointService.rewardReportPoint(report.getReporterId(), REPORT_REWARD_POINT);
            report.markRewarded();
        }

        // 피신고자 채택 누적 횟수 조회 (제재 정책용)
        int acceptedCount = reportRepository.countByTargetIdAndStatus(
                report.getTargetId(), ReportStatus.ACCEPTED);

        // 채택 횟수에 따른 피신고자 제재 및 알림 처리
        // case 1~2: 경고 (계정 정지 없음)
        // case 3: 3일 정지 / case 4: 10일 정지 / case 5: 30일 정지
        // default(6회 이상): 영구 정지
        switch (acceptedCount) {
            case 1 -> notificationPublisher.sendSystem(report.getTargetId(), "서비스 이용 경고",
                    "신고가 채택되었습니다. 서비스 이용 규정을 준수해 주세요.");

            case 2 -> notificationPublisher.sendSystem(report.getTargetId(), "서비스 이용 경고",
                    "두 번째 경고입니다. 재발 시 계정이 정지될 수 있습니다.");

            case 3 -> {
                userService.suspendUser(report.getTargetId(), 3);
                notificationPublisher.sendSystem(report.getTargetId(), "서비스 이용 제재 안내",
                        "세 번째 규정 위반으로 계정이 3일간 정지되었습니다.");
            }
            case 4 -> {
                userService.suspendUser(report.getTargetId(), 10);
                notificationPublisher.sendSystem(report.getTargetId(), "서비스 이용 제재 안내",
                        "네 번째 규정 위반으로 계정이 10일간 정지되었습니다.");
            }
            case 5 -> {
                userService.suspendUser(report.getTargetId(), 30);
                notificationPublisher.sendSystem(report.getTargetId(), "서비스 이용 제재 안내",
                        "다섯 번째 규정 위반으로 계정이 30일간 정지되었습니다.");
            }
            default -> {
                // 6회 이상 → 영구정지
                if (acceptedCount >= 6) {
                    userService.suspendUser(report.getTargetId(), null);
                    notificationPublisher.sendSystem(report.getTargetId(), "서비스 이용 제재 안내",
                            "지속적인 규정 위반으로 계정이 영구 정지되었습니다.");
                }
            }
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

        // 신고자의 기각 누적 횟수 조회
        int rejectedCount = reportRepository.countByReporterIdAndStatus(
                report.getReporterId(), ReportStatus.REJECTED);

        // 기각 3회 초과 시 신고 기능 10일 박탈
        if (rejectedCount > REPORT_BAN_THRESHOLD) {
            userService.banReportFeature(report.getReporterId(), REPORT_BAN_DAYS);
        }

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
