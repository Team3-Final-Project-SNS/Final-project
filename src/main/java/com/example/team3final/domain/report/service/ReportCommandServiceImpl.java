package com.example.team3final.domain.report.service;

import com.example.team3final.common.exception.ErrorCode;
import com.example.team3final.common.exception.ReportException;
import com.example.team3final.domain.admin.service.AdminService;
import com.example.team3final.domain.notification.service.NotificationPublisher;
import com.example.team3final.domain.post.entity.Post;
import com.example.team3final.domain.post.service.PostInternalService;
import com.example.team3final.domain.report.dto.request.CreateReportRequestDto;
import com.example.team3final.domain.report.dto.response.CreateReportResponseDto;
import com.example.team3final.domain.report.entity.Report;
import com.example.team3final.domain.report.enums.ReportStatus;
import com.example.team3final.domain.report.repository.ReportRepository;
import com.example.team3final.domain.user.service.UserModerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// Report 도메인의 신고 생성 기능을 담당하는 서비스
@Service
@RequiredArgsConstructor
@Transactional
public class ReportCommandServiceImpl implements ReportCommandService {

    private final ReportRepository reportRepository;
    private final PostInternalService postInternalService;
    private final UserModerationService userModerationService;
    private final AdminService adminService;
    private final NotificationPublisher notificationPublisher;

    // 신고 접수
    @Override
    public CreateReportResponseDto createReport(Long reporterId, CreateReportRequestDto request) {

        // 신고 접수 중 관리자 삭제가 동시에 들어올 수 있으므로 락 조회 사용.
        // 관리자 삭제도 같은 Post row를 잠그면 신고 생성/관리자 삭제 순서가 정리됨.
        Post post = postInternalService.getPostByIdWithLock(request.getTargetId());

        if (post.getAuthorId().equals(reporterId)) {
            throw new ReportException(ErrorCode.REPORT_SELF_REPORT);
        }

        if (userModerationService.isReportBanned(reporterId)) {
            throw new ReportException(ErrorCode.REPORT_FEATURE_BANNED);
        }

        // 중복 신고 방지
        if (reportRepository.existsByReporterIdAndTargetIdAndStatusIn(
                reporterId,
                request.getTargetId(),
                java.util.List.of(ReportStatus.PENDING, ReportStatus.ACCEPTED))) {
            throw new ReportException(ErrorCode.REPORT_ALREADY_REPORTED);   // REPORT_006
        }

        // 기각된 신고에 대해 3일 이내 재신고 제한
        // 단, 게시글이 기각 이후에 수정됐으면 재신고 허용
        reportRepository.findTopByReporterIdAndTargetIdAndStatusOrderByProcessedAtDesc(
                        reporterId, request.getTargetId(), ReportStatus.REJECTED)
                .ifPresent(rejectReport -> {

                    // 4-1) 기각 처리 시각이 지금 기준 3일 이내인지 (쿨다운 중인지)
                    boolean isWithin3Days = rejectReport.getProcessedAt()
                            .isAfter(LocalDateTime.now().minusDays(3));

                    // 4-2) 게시글이 '기각 이후'에 수정됐는지 (updatedAt > 기각 시각)
                    //      └ updatedAt이 null일 수도 있으니 null 체크 먼저
                    boolean isPostUpdatedAfterRejection =
                            post.getUpdatedAt() != null
                                    && post.getUpdatedAt().isAfter(rejectReport.getProcessedAt());

                    // 4-3) 3일 이내(쿨다운) AND 수정 안 됨 → 재신고 차단
                    //      반대로, 수정됐으면(isPostUpdatedAfterRejection=true) 통과 → 재신고 허용
                    if (isWithin3Days && !isPostUpdatedAfterRejection) {
                        throw new ReportException(ErrorCode.REPORT_TOO_SOON);   // REPORT_004
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

        // 26. 신고 접수 알림 - 활성 관리자 모두에게
        adminService.getActiveAdminIds().forEach(
                adminId -> notificationPublisher.sendReportSubmitted(adminId, savedReport.getId())
        );

        return CreateReportResponseDto.from(savedReport);
    }
}
