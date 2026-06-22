package com.example.team3final.domain.ai.report.tool;

import org.springframework.ai.tool.execution.ToolCallResultConverter;

import java.lang.reflect.Type;
import java.util.List;

/**
 * 신고 AI Tool 조회 결과를 LLM이 읽기 쉬운 한국어 텍스트로 변환합니다.
 *
 * record 형태의 내부 조회 결과를 프롬프트 컨텍스트에 들어갈 문자열로 바꿔
 * 신고 분석과 고위험 유저 판단 근거가 명확하게 전달되도록 합니다.
 */
public class AiReportToolResultConverter implements ToolCallResultConverter {

    /**
     * Tool 호출 결과 객체를 LLM 프롬프트에 삽입 가능한 문자열로 변환합니다.
     *
     * 신고 단건 분석 결과는 formatContext로 변환하고,
     * 고위험 유저 후보 목록은 각 후보를 formatHighRiskUser로 변환해 이어 붙입니다.
     * 알 수 없는 결과 타입이나 빈 목록은 LLM이 추측하지 않도록 명시적인 기본 문구를 반환합니다.
     */
    @Override
    public String convert(Object result, Type returnType) {
        if (result instanceof AiReportContextToolResult context) {
            return formatContext(context);
        }

        if (result instanceof AiReportDashboardToolResult dashboard) {
            return formatDashboard(dashboard);
        }

        if (result instanceof AiDisputeContextToolResult dispute) {
            return formatDispute(dispute);
        }

        if (result instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                if (item instanceof AiReportHighRiskUserToolResult user) {
                    sb.append(formatHighRiskUser(user)).append("\n");
                } else if (item instanceof AiReportSearchToolResult report) {
                    sb.append(formatReportSearchResult(report)).append("\n");
                } else if (item instanceof AiDisputeSearchToolResult dispute) {
                    sb.append(formatDisputeSearchResult(dispute)).append("\n");
                }
            }
            return sb.isEmpty() ? "조회 결과가 없습니다." : sb.toString();
        }

        return "조회 결과가 없습니다.";
    }

    private String formatDashboard(AiReportDashboardToolResult dashboard) {
        return String.format(
                """
                관리자 콘솔 운영 요약
                전체 처리 대기 업무: %d건
                게시글: 전체 %d건, 모집 중 %d건, 매칭 완료 %d건, 만료 %d건
                신고: 전체 %d건, 처리 대기 %d건, 채택 %d건, 기각 %d건
                고객 문의: 전체 %d건, 답변 대기 %d건, 답변 완료 %d건
                이의제기: 전체 %d건, 검토 대기 %d건, 제출 %d건, 검토 중 %d건, 보류 %d건, 수용 %d건, 부분 수용 %d건, 기각 %d건
                유저: 전체 %d명, 활성 %d명, 정지 %d명, 탈퇴 %d명
                결제: 전체 %d건, 결제 대기 %d건, 결제 완료 %d건, 취소 %d건, 실패 %d건, 완료 결제 금액 합계 %d원
                오늘 결제: 완료 금액 %d원, 완료 %d건, 대기 %d건, 취소 %d건, 실패 %d건
                오늘 결제 관리자 확인 필요 여부: %s
                """,
                dashboard.totalPendingWorkCount(),
                dashboard.totalPostCount(),
                dashboard.openPostCount(),
                dashboard.matchedPostCount(),
                dashboard.expiredPostCount(),
                dashboard.totalReportCount(),
                dashboard.pendingReportCount(),
                dashboard.acceptedReportCount(),
                dashboard.rejectedReportCount(),
                dashboard.totalInquiryCount(),
                dashboard.pendingInquiryCount(),
                dashboard.answeredInquiryCount(),
                dashboard.totalDisputeCount(),
                dashboard.openDisputeCount(),
                dashboard.submittedDisputeCount(),
                dashboard.underReviewDisputeCount(),
                dashboard.holdDisputeCount(),
                dashboard.acceptedDisputeCount(),
                dashboard.partiallyAcceptedDisputeCount(),
                dashboard.rejectedDisputeCount(),
                dashboard.totalUserCount(),
                dashboard.activeUserCount(),
                dashboard.suspendedUserCount(),
                dashboard.withdrawnUserCount(),
                dashboard.totalPaymentCount(),
                dashboard.readyPaymentCount(),
                dashboard.paidPaymentCount(),
                dashboard.cancelledPaymentCount(),
                dashboard.failedPaymentCount(),
                dashboard.paidPaymentAmount(),
                dashboard.todayPaidPaymentAmount(),
                dashboard.todayPaidPaymentCount(),
                dashboard.todayReadyPaymentCount(),
                dashboard.todayCancelledPaymentCount(),
                dashboard.todayFailedPaymentCount(),
                dashboard.todayCancelledPaymentCount() > 0 || dashboard.todayFailedPaymentCount() > 0
                        ? "필요 - 오늘 결제 취소 또는 실패 건이 있습니다."
                        : "불필요 - 오늘 결제 취소와 실패 건이 없습니다."
        );
    }

    private String formatDispute(AiDisputeContextToolResult dispute) {
        StringBuilder recentMessages = new StringBuilder();
        for (AiDisputeContextToolResult.ChatMessage message : dispute.recentChatMessages()) {
            recentMessages.append("- ")
                    .append(message.createdAt())
                    .append(" / ")
                    .append(blankToDefault(message.senderNickname()))
                    .append(": ")
                    .append(blankToDefault(message.content()))
                    .append("\n");
        }

        return String.format(
                """
                이의제기 ID: %d
                매칭 ID: %d
                제출자: %s
                이의제기 유형: %s
                이의제기 상세 사유: %s
                이의제기 상태: %s
                만남 인증 상태: %s
                등록자 GPS 인증 시각: %s
                신청자 GPS 인증 시각: %s
                제출 시각: %s
                관련 채팅 메시지 수: %d
                최근 채팅 일부:
                %s
                """,
                dispute.disputeId(),
                dispute.matchId(),
                blankToDefault(dispute.applicantNickname()),
                dispute.disputeType(),
                blankToDefault(dispute.reason()),
                dispute.status(),
                dispute.verificationStatus(),
                dispute.authorPlaceVerifiedAt() == null ? "없음" : dispute.authorPlaceVerifiedAt(),
                dispute.applicantPlaceVerifiedAt() == null ? "없음" : dispute.applicantPlaceVerifiedAt(),
                dispute.submittedAt(),
                dispute.chatMessageCount(),
                recentMessages.isEmpty() ? "채팅 정보 없음" : recentMessages.toString()
        );
    }

    /**
     * 신고 단건 분석에 필요한 Tool 결과를 사람이 읽기 쉬운 한국어 컨텍스트로 변환합니다.
     *
     * 신고 사유, 신고 상태, 신고 상세, 신고자, 대상 게시글,
     * 피신고 유저의 누적 신고 현황을 한 번에 전달하여
     * LLM이 제공된 데이터 안에서만 위험도와 처리 제안을 판단하도록 돕습니다.
     */
    private String formatContext(AiReportContextToolResult context) {
        return String.format(
                """
                신고 ID: %d
                신고 사유: %s
                신고 상태: %s
                신고 상세: %s
                신고자: %s(%d)
                대상 게시글 ID: %d
                대상 게시글 존재 여부: %s
                피신고 유저: %s(%s)
                게시글 장소: %s
                게시글 시간: %s
                게시글 내용: %s
                피신고 유저 전체 신고 수: %d
                피신고 유저 대기 신고 수: %d
                피신고 유저 채택 신고 수: %d
                """,
                context.reportId(),
                context.reportReason(),
                context.reportStatus(),
                blankToDefault(context.reportDetail()),
                blankToDefault(context.reporterNickname()),
                context.reporterId(),
                context.targetPostId(),
                context.targetPostFound() ? "존재" : "없음",
                blankToDefault(context.targetUserNickname()),
                context.targetUserId() == null ? "알 수 없음" : context.targetUserId(),
                blankToDefault(context.targetPlaceName()),
                blankToDefault(context.targetMeetAt()),
                blankToDefault(context.targetPostContent()),
                context.targetUserTotalReportCount(),
                context.targetUserPendingReportCount(),
                context.targetUserAcceptedReportCount()
        );
    }

    private String formatReportSearchResult(AiReportSearchToolResult report) {
        return String.format(
                """
                신고 검색 결과
                신고 ID: %d
                신고 사유: %s
                신고 상태: %s
                신고 상세: %s
                신고자: %s(%d)
                대상 게시글 ID: %d
                피신고 유저: %s(%s)
                게시글 장소: %s
                게시글 시간: %s
                게시글 내용: %s
                """,
                report.reportId(),
                report.reportReason(),
                report.reportStatus(),
                blankToDefault(report.reportDetail()),
                blankToDefault(report.reporterNickname()),
                report.reporterId(),
                report.targetPostId(),
                blankToDefault(report.targetUserNickname()),
                report.targetUserId() == null ? "알 수 없음" : report.targetUserId(),
                blankToDefault(report.targetPlaceName()),
                blankToDefault(report.targetMeetAt()),
                blankToDefault(report.targetPostContent())
        );
    }

    private String formatDisputeSearchResult(AiDisputeSearchToolResult dispute) {
        return String.format(
                """
                이의제기 검색 결과
                이의제기 ID: %d
                매칭 ID: %d
                제출자: %s(%d)
                이의제기 유형: %s
                이의제기 상태: %s
                제출 사유: %s
                제출 시각: %s
                """,
                dispute.disputeId(),
                dispute.matchId(),
                blankToDefault(dispute.submitterNickname()),
                dispute.submitterId(),
                dispute.disputeType(),
                dispute.status(),
                blankToDefault(dispute.reason()),
                dispute.submittedAt()
        );
    }

    /**
     * 고위험 유저 후보 Tool 결과를 LLM이 비교하기 쉬운 텍스트로 변환합니다.
     *
     * 전체 신고 수, 대기 신고 수, 채택 신고 수, 기각 신고 수를 함께 제공해
     * LLM이 반복 신고와 실제 채택 이력을 구분해서 위험도를 설명할 수 있게 합니다.
     */
    private String formatHighRiskUser(AiReportHighRiskUserToolResult user) {
        return String.format(
                """
                유저 ID: %d
                닉네임: %s
                전체 신고 수: %d
                대기 신고 수: %d
                채택 신고 수: %d
                기각 신고 수: %d
                관련 신고 ID: %s
                신고 사유 요약: %s
                """,
                user.userId(),
                blankToDefault(user.nickname()),
                user.totalReportCount(),
                user.pendingReportCount(),
                user.acceptedReportCount(),
                user.rejectedReportCount(),
                user.relatedReportIds(),
                blankToDefault(user.reasonSummary())
        );
    }

    /**
     * null 또는 빈 문자열을 "정보 없음"으로 치환합니다.
     *
     * Tool 결과 일부가 비어 있어도 LLM 프롬프트에 null/blank가 그대로 들어가지 않게 하여
     * 불필요한 추측과 환각 가능성을 줄입니다.
     */
    private String blankToDefault(String value) {
        return value == null || value.isBlank() ? "정보 없음" : value;
    }
}
