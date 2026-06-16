package com.example.team3final.domain.ai.report.tool;

import com.example.team3final.domain.report.enums.ReportReason;
import com.example.team3final.domain.report.enums.ReportStatus;

/**
 * 관리자 AI가 닉네임, 게시글 장소, 게시글 한마디 같은 자연어 키워드로
 * 신고 후보를 찾았을 때 사용하는 검색 결과입니다.
 */
public record AiReportSearchToolResult(
        Long reportId,
        ReportReason reportReason,
        ReportStatus reportStatus,
        String reportDetail,
        Long reporterId,
        String reporterNickname,
        Long targetPostId,
        Long targetUserId,
        String targetUserNickname,
        String targetPostContent,
        String targetPlaceName,
        String targetMeetAt
) {
}
