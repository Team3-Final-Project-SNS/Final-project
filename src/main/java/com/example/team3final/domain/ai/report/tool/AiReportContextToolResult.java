package com.example.team3final.domain.ai.report.tool;

import com.example.team3final.domain.report.enums.ReportReason;
import com.example.team3final.domain.report.enums.ReportStatus;

/**
 * 단일 신고 분석에 필요한 내부 데이터 조회 결과입니다.
 *
 * 신고 내용, 신고자, 대상 게시글, 피신고 유저, 누적 신고 수를 한 번에 담아
 * AiReportToolResultConverter가 LLM 컨텍스트 문자열로 변환할 수 있게 합니다.
 */
public record AiReportContextToolResult(
        Long reportId,
        ReportReason reportReason,
        ReportStatus reportStatus,
        String reportDetail,
        Long reporterId,
        String reporterNickname,
        Long targetPostId,
        boolean targetPostFound,
        Long targetUserId,
        String targetUserNickname,
        String targetPostContent,
        String targetPlaceName,
        String targetMeetAt,
        int targetUserTotalReportCount,
        int targetUserPendingReportCount,
        int targetUserAcceptedReportCount
) {
}
