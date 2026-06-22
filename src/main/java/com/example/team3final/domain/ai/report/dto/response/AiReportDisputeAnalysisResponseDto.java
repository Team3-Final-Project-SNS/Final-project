package com.example.team3final.domain.ai.report.dto.response;

import com.example.team3final.domain.ai.report.tool.AiDisputeContextToolResult;
import com.example.team3final.domain.dispute.enums.DisputeStatus;
import com.example.team3final.domain.dispute.enums.DisputeType;
import com.example.team3final.domain.meet.enums.VerificationStatus;

import java.time.LocalDateTime;

/**
 * 관리자 AI 챗봇에서 이의제기 단건 분석 결과를 반환하는 DTO입니다.
 *
 * AI가 최종 판정을 내리지 않고, 관리자가 판단할 수 있도록
 * 현재 상태와 확인해야 할 근거를 요약해서 제공합니다.
 */
public record AiReportDisputeAnalysisResponseDto(
        Long disputeId,
        Long matchId,
        String applicantNickname,
        DisputeType disputeType,
        String reason,
        DisputeStatus status,
        VerificationStatus verificationStatus,
        LocalDateTime authorPlaceVerifiedAt,
        LocalDateTime applicantPlaceVerifiedAt,
        LocalDateTime submittedAt,
        int chatMessageCount,
        String summary,
        String evidence,
        String actionGuide,
        boolean needsAdminReview
) {
    public static AiReportDisputeAnalysisResponseDto of(AiDisputeContextToolResult context) {
        boolean hasAuthorGps = context.authorPlaceVerifiedAt() != null;
        boolean hasApplicantGps = context.applicantPlaceVerifiedAt() != null;

        String evidence = """
                - 이의제기 사유: %s
                - 제출 상세: %s
                - 현재 이의제기 상태: %s
                - 만남 인증 상태: %s
                - 등록자 GPS 인증 시각: %s
                - 신청자 GPS 인증 시각: %s
                - 관련 채팅 메시지 수: %d건
                """.formatted(
                context.disputeType(),
                context.reason(),
                context.status(),
                context.verificationStatus(),
                hasAuthorGps ? context.authorPlaceVerifiedAt() : "없음",
                hasApplicantGps ? context.applicantPlaceVerifiedAt() : "없음",
                context.chatMessageCount()
        );

        String actionGuide = """
                관리자 확인 순서:
                1. 제출 사유와 증빙자료가 이의제기 유형에 맞는지 확인합니다.
                2. GPS 인증 시각과 만남 인증 상태를 비교합니다.
                3. 관련 채팅 기록에서 지각 공지, 연장 요청, 인증 오류 정황을 확인합니다.
                4. 정책 기준에 따라 ACCEPTED, PARTIALLY_ACCEPTED, REJECTED, HOLD 중 하나로 최종 판정합니다.
                """;

        return new AiReportDisputeAnalysisResponseDto(
                context.disputeId(),
                context.matchId(),
                context.applicantNickname(),
                context.disputeType(),
                context.reason(),
                context.status(),
                context.verificationStatus(),
                context.authorPlaceVerifiedAt(),
                context.applicantPlaceVerifiedAt(),
                context.submittedAt(),
                context.chatMessageCount(),
                "%d번 이의제기 검토에 필요한 운영 데이터를 조회했습니다. AI는 최종 판정 대신 확인 근거와 검토 순서를 제공합니다."
                        .formatted(context.disputeId()),
                evidence,
                actionGuide,
                true
        );
    }
}
