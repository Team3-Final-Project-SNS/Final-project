package com.example.team3final.domain.ai.report.dashboard.service;

import com.example.team3final.domain.ai.report.dashboard.dto.AiReportDashboardSnapshotDto;

/**
 * 관리자 콘솔 챗봇이 사용할 대시보드 요약 조회 계약입니다.
 *
 * AI Tool이 Repository를 직접 참조하지 않도록 서비스 계층으로 분리했습니다.
 */
public interface AiReportDashboardQueryService {

    AiReportDashboardSnapshotDto getSnapshot();
}
