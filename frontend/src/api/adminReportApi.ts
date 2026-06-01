import axiosInstance from "./axiosInstance";
import { ApiResponse } from "./authApi";
import { PageResponse } from "./postApi";
import { ReportReason, ReportStatus } from "./reportApi";

export interface AdminReportItem {
  reportId: number;
  reporterNickname: string;
  targetId: number;
  reason: ReportReason;
  detail: string | null;
  status: ReportStatus;
  createdAt: string;
}

export interface AdminProcessReportResponse {
  reportId: number;
  status: ReportStatus;
  isRewarded: boolean;
  rewardPoint: number;
  processedAt: string;
}

export const getAdminReports = (status?: ReportStatus, page: number = 0, size: number = 20) =>
  axiosInstance.get<ApiResponse<PageResponse<AdminReportItem>>>("/api/v1/admin/reports", {
    params: { status, page, size },
  });

export const processAdminReport = (reportId: number, reportStatus: "ACCEPTED" | "REJECTED", comment?: string) =>
  axiosInstance.patch<ApiResponse<AdminProcessReportResponse>>(`/api/v1/admin/reports/${reportId}/process`, {
    reportStatus,
    comment,
  });
