package com.example.team3final.domain.report.dto.response;

import com.example.team3final.domain.report.entity.Report;
import com.example.team3final.domain.report.enums.ReportReason;
import com.example.team3final.domain.report.enums.ReportStatus;
import java.time.LocalDateTime;

// 내 신고 내역 조회
public record GetMyReportsResponseDto(
        Long reportId,                       // 신고 ID
        Long targetId,                       // 신고 대상 ID
        ReportReason reason,                 // 신고 사유
        ReportStatus status,                 // 처리 상태 (PENDING / ACCEPTED / REJECTED / WITHDRAWN)
        LocalDateTime createdAt              // 신고 접수 시각
) {
    public static GetMyReportsResponseDto from(Report report) {
        return new GetMyReportsResponseDto(
                report.getId(),
                report.getTargetId(),
                report.getReason(),
                report.getStatus(),
                report.getCreatedAt()
        );
    }
}
