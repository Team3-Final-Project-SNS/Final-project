package com.example.team3final.domain.report.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.ReportException;
import com.example.team3final.domain.notification.service.NotificationPublisher;
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

        // 월별 상한 (300P) 미만일 때만 포상 지급
        if (rewardedThisMonth < MONTHLY_REWARD_LIMIT) {
            userPointService.rewardReportPoint(report.getReporterId(), REPORT_REWARD_POINT);
            report.markRewarded();
        }

        // 피신고자 채택 누적 횟수 조회 (제재 정책용)
        int acceptedCount = reportRepository.countByTargetIdAndStatus(
                report.getTargetId(), ReportStatus.ACCEPTED);

        // 35. 계정 정지 알림 - 해당 사용자에게
        // case 1~2: 경고 (계정 정지 없음)
        // case 3: 3일 정지 / case 4: 10일 정지 / case 5: 30일 정지
        // default(6회 이상): 영구 정지
        switch (acceptedCount) {
            case 1 -> notificationPublisher.sendPostWarned(
                    report.getTargetId(),  // userId (Long)
                    "서비스 이용 경고",      // title (String)
                    "신고가 접수되었습니다. 서비스 이용 규정을 준수해 주세요." // content (String)
            );

            case 2 -> notificationPublisher.sendPostWarned(
                    report.getTargetId(),  // userId (Long)
                    "서비스 이용 경고",      // title (String)
                    "두 번째 규정 위반 경고입니다. 서비스 이용 규정을 준수해 주세요. 이후 신고 접수 시 계정이 정지됩니다. " // content (String)
            );

            case 3 -> {
                userModerationService.suspendUser(report.getTargetId(), 3);
                notificationPublisher.sendAccountSuspended(
                        report.getTargetId(),  // userId (Long)
                        "서비스 이용 제재 안내",    // title (String)
                        "세 번째 규정 위반으로 계정이 3일간 정지되었습니다." // content (String)
                );
            }
            case 4 -> {
                userModerationService.suspendUser(report.getTargetId(), 10);
                notificationPublisher.sendAccountSuspended(
                        report.getTargetId(),  // userId (Long)
                        "서비스 이용 제재 안내",    // title (String)
                        "네 번째 규정 위반으로 계정이 10일간 정지되었습니다." // content (String)
                );
            }
            case 5 -> {
                userModerationService.suspendUser(report.getTargetId(), 30);
                notificationPublisher.sendAccountSuspended(
                        report.getTargetId(),  // userId (Long)
                        "서비스 이용 제재 안내",    // title (String)
                        "다섯 번째 규정 위반으로 계정이 30일간 정지되었습니다." // content (String)
                );
            }

            default -> {
                // 6회 이상 → 영구정지
                if (acceptedCount >= 6) {
                    userModerationService.suspendUser(report.getTargetId(), null);
                    notificationPublisher.sendAccountSuspended(
                            report.getTargetId(),  // userId (Long)
                            "서비스 이용 제재 안내",    // title (String)
                            "지속적인 규정 위반으로 계정이 영구 정지되었습니다." // content (String)
                    );
                }
            }
        }
        // 27. 신고 채택 포인트 지급 알림 - 신고자에게
        notificationPublisher.sendReportAcceptedPoint(report.getReporterId(), reportId);
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
