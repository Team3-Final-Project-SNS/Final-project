package com.example.team3final.domain.report.dto.response;

import java.time.LocalDateTime;

// 신고 취소
public record DeleteReportResponseDto(
        Long reportId,                         // 취소된 신고 ID
        LocalDateTime cancelledAt              // 신고 취소 시각
) {
    public static DeleteReportResponseDto of(Long reportId, LocalDateTime cancelledAt) {
        return new DeleteReportResponseDto(reportId, cancelledAt);
    }
}
