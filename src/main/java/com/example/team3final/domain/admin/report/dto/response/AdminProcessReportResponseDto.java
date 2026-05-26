package com.example.team3final.domain.admin.report.dto.response;

import com.example.team3final.domain.report.entity.Report;
import com.example.team3final.domain.report.enums.ReportStatus;

import java.time.LocalDateTime;

public record AdminProcessReportResponseDto (

        Long reportId,            // 처리된 신고 ID
        ReportStatus status,      // 처리 결과 (ACCEPTED / REJECTED)
        boolean isRewarded,       // 포상 지급 여부 (ACCEPTED 시 true)
        int rewardPoint,          // 지급된 포인트 (ACCEPTED=50, REJECTED=0)
        LocalDateTime processedAt // 처리 시각
) {

    public static AdminProcessReportResponseDto from(Report report, int rewardPoint) {
        return new AdminProcessReportResponseDto(
                report.getId(),
                report.getStatus(),
                report.isRewarded(),
                rewardPoint,
                report.getProcessedAt()
        );
    }
}
