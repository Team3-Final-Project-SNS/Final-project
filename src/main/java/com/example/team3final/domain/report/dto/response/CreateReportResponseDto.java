package com.example.team3final.domain.report.dto.response;

import com.example.team3final.domain.report.entity.Report;
import com.example.team3final.domain.report.enums.ReportTargetType;

import java.time.LocalDateTime;

// 신고 접수
public record CreateReportResponseDto(
        Long reportId,                       // 신고 ID
        ReportTargetType targetType,         // 신고 대상 유형 (POST / USER / CHAT_MESSAGE)
        Long targetId,                       // 신고 대상 ID
        String status,                       // 처리 상태 (항상 PENDING으로 시작)
        LocalDateTime createdAt              // 신고 접수 시각
) {
    public static CreateReportResponseDto from(Report report) {
        return new CreateReportResponseDto(
                report.getId(),
                report.getTargetType(),
                report.getTargetId(),
                report.getStatus().name(),
                report.getCreatedAt()
        );
    }
}
