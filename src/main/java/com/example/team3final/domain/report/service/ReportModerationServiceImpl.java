package com.example.team3final.domain.report.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.ReportException;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.service.PostInternalService;
import com.example.team3final.domain.report.entity.Report;
import com.example.team3final.domain.report.enums.ReportStatus;
import com.example.team3final.domain.report.repository.ReportRepository;
import com.example.team3final.domain.user.service.UserModerationService;
import com.example.team3final.domain.user.service.UserPointService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// Report 도메인의 관리자 신고 처리 기능을 담당하는 서비스
@Service
@RequiredArgsConstructor
@Transactional
public class ReportModerationServiceImpl implements ReportModerationService {

    private final ReportRepository reportRepository;
    private final UserPointService userPointService;
    private final NotificationPublisher notificationPublisher;
    private final UserModerationService userModerationService;
    private final PostInternalService postInternalService;

    // 포상 지급 포인트
    private static final int REPORT_REWARD_POINT = 50;
    // 기각 횟수가 이 값의 배수가 될 때마다 신고 기능 박탈 (3, 6, 9...)
    private static final int REPORT_BAN_THRESHOLD = 3;
    // 10일 박탈에 활용
    private static final int REPORT_BAN_DAYS = 10;
    // 월 포상 상한
    private static final int MONTHLY_REWARD_LIMIT = 300;

    // 신고 채택 - 관리자 호출용
    @Override
    public void acceptReport(Long reportId, Long adminId) {

        // 조건부 UPDATE — PENDING인 경우에만 ACCEPTED로 전환 (원자적)
        //    두 관리자가 동시에 호출해도 DB가 한 건만 처리함
        int updated = reportRepository.acceptIfPending(reportId, adminId);

        // 영향받은 행이 0이면 이미 처리된 신고 -> 예외
        if (updated == 0) {
            throw new ReportException(ErrorCode.REPORT_ALREADY_PROCESSED);
        }

        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ReportException(ErrorCode.REPORT_NOT_FOUND));

        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1)
                // 이번 달 1일 00:00:00
                .withHour(0).withMinute(0).withSecond(0).withNano(0);

        // 이번 달 포상 지급 횟수 조회
        int rewardedCountThisMonth = reportRepository.countRewardedThisMonth(report.getReporterId(), startOfMonth);

        // 이번 달 지급 총액 = 횟수 * 50P
        int rewardedThisMonth = rewardedCountThisMonth * REPORT_REWARD_POINT;

        // 실제 포인트 지급 여부를 기록하여 알림 문구와 지급 결과를 일치시킨다.
        boolean rewardGranted = rewardedThisMonth < MONTHLY_REWARD_LIMIT;

        if (rewardGranted) {
            userPointService.rewardReportPoint(
                    report.getReporterId(),
                    REPORT_REWARD_POINT
            );
            report.markRewarded();
        }

        // Report.targetId는 userId가 아니라 postId다.
        // 삭제된 게시글도 신고 처리할 수 있도록 삭제 포함 조회를 사용한다.
        Long targetUserId = postInternalService
                .getPostByIdIncludingDeleted(report.getTargetId())
                .getAuthorId();

        // 제재 횟수는 한 게시글이 아닌 해당 작성자의 전체 게시글을 기준으로 계산한다.
        int acceptedCount =
                reportRepository.countAcceptedReportsByAuthorId(targetUserId);

        switch (acceptedCount) {
            case 1 -> notificationPublisher.sendPostWarned(
                    targetUserId,
                    "서비스 이용 경고",
                    "규정 위반 신고가 접수되어 경고 처리되었습니다. 서비스 이용 규정을 준수해 주세요."
            );

            case 2 -> notificationPublisher.sendPostWarned(
                    targetUserId,
                    "서비스 이용 경고",
                    "두 번째 규정 위반 경고입니다. 이후 신고 접수 시 계정이 정지됩니다."
            );

            case 3 -> {
                userModerationService.applyReportSuspension(targetUserId, 3);
                notificationPublisher.sendAccountSuspended(
                        targetUserId, "서비스 이용 제재 안내",
                        "누적 신고 3회에 따른 3일 계정 정지 제재가 적용되었습니다."
                );
            }

            case 4 -> {
                userModerationService.applyReportSuspension(targetUserId, 10);
                notificationPublisher.sendAccountSuspended(
                        targetUserId, "서비스 이용 제재 안내",
                        "누적 신고 4회에 따른 10일 계정 정지 제재가 적용되었습니다."
                );
            }

            case 5 -> {
                userModerationService.applyReportSuspension(targetUserId, 30);
                notificationPublisher.sendAccountSuspended(
                        targetUserId, "서비스 이용 제재 안내",
                        "누적 신고 5회에 따른 30일 계정 정지 제재가 적용되었습니다."
                );
            }

            default -> {
                if (acceptedCount >= 6) {
                    userModerationService.applyReportSuspension(targetUserId, null);
                    notificationPublisher.sendAccountSuspended(
                            targetUserId, "서비스 이용 제재 안내",
                            "지속적인 규정 위반으로 계정이 영구 정지되었습니다."
                    );
                }
            }
        }

        // 포인트 지급 여부에 따라 사실과 일치하는 알림을 보낸다.
        if (rewardGranted) {
            notificationPublisher.sendReportAcceptedPoint(
                    report.getReporterId(), reportId
            );
        } else {
            notificationPublisher.sendReportAcceptedWithoutPoint(
                    report.getReporterId(), reportId
            );
        }
    }

    // 신고 기각 - 관리자 호출용
    @Override
    public void rejectReport(Long reportId, Long adminId) {

        // PENDING인 경우에만 REJECTED로 전환
        int updated = reportRepository.rejectIfPending(reportId, adminId);

        if (updated == 0) {
            throw new ReportException(ErrorCode.REPORT_ALREADY_PROCESSED);
        }

        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ReportException(ErrorCode.REPORT_NOT_FOUND));

        // 신고자(신고를 한 사람)의 기각 누적 횟수 조회
        int rejectedCount = reportRepository.countByReporterIdAndStatus(
                report.getReporterId(), ReportStatus.REJECTED);

        // 기각 횟수가 3의 배수(3, 6, 9...)가 될 때마다 신고 기능 10일 박탈
        if (rejectedCount > 0 && rejectedCount % REPORT_BAN_THRESHOLD == 0) {  // count가 3의 배수일 때 기능 박탈
            userModerationService.banReportFeature(report.getReporterId(), REPORT_BAN_DAYS);
        }

        // 28. 신고 기각 알림 - 신고자에게
        notificationPublisher.sendReportRejected(report.getReporterId(), reportId);
    }


}
