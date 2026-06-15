package com.example.team3final.domain.report.service;

// Report 도메인의 관리자 신고 처리 기능을 담당하는 서비스
public interface ReportModerationService {

    // 신고 채택 - 관리자 호출용
    void acceptReport(Long reportId, Long adminId);

    // 신고 기각 - 관리자 호출용
    void rejectReport(Long reportId, Long adminId);
}
