package com.example.team3final.domain.admin.report.dto.response;

import com.example.team3final.domain.report.entity.Report;
import com.example.team3final.domain.report.enums.ReportReason;
import com.example.team3final.domain.report.enums.ReportStatus;

import java.time.LocalDateTime;

public record AdminGetReportResponseDto (

        Long reportId,           // 신고 ID
        String reporterNickname, // 신고자 닉네임 (UserService에서 별도 조회해서 주입)
        Long targetId,           // 신고 대상 게시글 ID
        ReportReason reason,     // 신고 사유 (SPAM / OBSCENE / FRAUD / ABUSE / OTHER)
        String detail,           // 신고 상세 내용
        ReportStatus status,     // 처리 상태 (PENDING / ACCEPTED / REJECTED / WITHDRAWN)
        LocalDateTime createdAt  // 신고 접수 시각
) {
    public static AdminGetReportResponseDto of(Report report, String reporterNickname) {
        return new AdminGetReportResponseDto(
                report.getId(),
                reporterNickname,
                report.getTargetId(),
                report.getReason(),
                report.getDetail(),
                report.getStatus(),
                report.getCreatedAt()
        );
    }
}
