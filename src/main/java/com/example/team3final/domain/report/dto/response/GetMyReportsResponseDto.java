package com.example.team3final.domain.report.dto.response;

import com.example.team3final.domain.report.entity.Report;
import com.example.team3final.domain.report.enums.ReportReason;
import com.example.team3final.domain.report.enums.ReportStatus;
import com.example.team3final.domain.report.enums.ReportTargetType;

import java.time.LocalDateTime;

// 내 신고 내역 조회
public record GetMyReportsResponseDto(
        Long reportId,                       // 신고 ID
        ReportTargetType targetType,         // 신고 대상 유형 (POST / USER / CHAT_MESSAGE)
        Long targetId,                       // 신고 대상 ID
        ReportReason reason,                 // 신고 사유
        ReportStatus status,                 // 처리 상태 (PENDING / ACCEPTED / REJECTED / WITHDRAWN)
        boolean isRewarded,                  // 포상 지급 여부 (채택 시 50P 지급)
        LocalDateTime createdAt              // 신고 접수 시각
) {
    public static GetMyReportsResponseDto from(Report report) {
        return new GetMyReportsResponseDto(
                report.getId(),
                report.getTargetType(),
                report.getTargetId(),
                report.getReason(),
                report.getStatus(),
                report.isRewarded(),
                report.getCreatedAt()
        );
    }
}
