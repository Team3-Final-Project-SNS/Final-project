package com.example.team3final.domain.ai.report.tool;

/**
 * 관리자 콘솔 대시보드에서 AI가 안내에 사용할 수 있는 운영 요약입니다.
 *
 * AI가 실제 삭제, 환불, 정지 같은 조치를 실행하지 않고도
 * 현재 관리자 화면에서 우선 확인해야 할 영역을 설명할 수 있게 최소 카운트만 제공합니다.
 */
public record AiReportDashboardToolResult(
        long totalPostCount,
        long openPostCount,
        long matchedPostCount,
        long expiredPostCount,
        long totalReportCount,
        long pendingReportCount,
        long acceptedReportCount,
        long rejectedReportCount,
        long totalInquiryCount,
        long pendingInquiryCount,
        long answeredInquiryCount,
        long totalDisputeCount,
        long openDisputeCount,
        long submittedDisputeCount,
        long underReviewDisputeCount,
        long holdDisputeCount,
        long acceptedDisputeCount,
        long partiallyAcceptedDisputeCount,
        long rejectedDisputeCount,
        long totalPendingWorkCount,
        long totalUserCount,
        long activeUserCount,
        long suspendedUserCount,
        long withdrawnUserCount,
        long totalPaymentCount,
        long readyPaymentCount,
        long paidPaymentCount,
        long cancelledPaymentCount,
        long failedPaymentCount,
        long paidPaymentAmount
) {
}
