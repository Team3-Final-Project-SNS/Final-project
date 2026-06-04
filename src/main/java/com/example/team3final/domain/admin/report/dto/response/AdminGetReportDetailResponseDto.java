package com.example.team3final.domain.admin.report.dto.response;

import com.example.team3final.domain.report.entity.Report;
import com.example.team3final.domain.report.enums.ReportReason;
import com.example.team3final.domain.report.enums.ReportStatus;

import java.time.LocalDateTime;

// 관리자 신고 상세조회 API
public record AdminGetReportDetailResponseDto (

        String reporterNickname,  // 신고자 닉네임
        Long targetId,            // 신고 대상 게시글 ID
        ReportReason reason,      // 신고 사유 (SPAM/OBSCENE/FRAUD/ABUSE/OTHER)
        String detail,            // 신고 상세 내용 (없으면 null)
        ReportStatus status,      // 처리 상태 (PENDING/ACCEPTED/REJECTED/WITHDRAWN)
        LocalDateTime processedAt,// 처리 완료 시각 (미처리 시 null)
        LocalDateTime createdAt   // 신고 접수 시각
) {
    public static AdminGetReportDetailResponseDto of(Report report, String reporterNickname) {
        return new AdminGetReportDetailResponseDto(
                reporterNickname,
                report.getTargetId(),
                report.getReason(),
                report.getDetail(),
                report.getStatus(),
                report.getProcessedAt(),  // 미처리 시 null
                report.getCreatedAt()
        );
    }
}
