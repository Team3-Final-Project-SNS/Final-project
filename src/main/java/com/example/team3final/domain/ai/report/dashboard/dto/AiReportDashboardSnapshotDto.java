package com.example.team3final.domain.ai.report.dashboard.dto;

/**
 * 관리자 콘솔 챗봇에서 사용하는 운영 현황 요약 DTO입니다.
 */
public record AiReportDashboardSnapshotDto(
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
