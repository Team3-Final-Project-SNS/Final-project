import axiosInstance from "./axiosInstance";
import { ApiResponse } from "./authApi";
import { PageResponse } from "./postApi";

export type ReportReason = "SPAM" | "OBSCENE" | "FRAUD" | "ABUSE" | "OTHER";
export type ReportStatus = "PENDING" | "ACCEPTED" | "REJECTED" | "WITHDRAWN";

export interface CreateReportRequest {
  targetId: number;
  reason: ReportReason;
  detail?: string;
}

export interface CreateReportResponse {
  reportId: number;
  targetId: number;
  status: ReportStatus;
  createdAt: string;
}

export interface MyReportItem {
  reportId: number;
  targetId: number;
  reason: ReportReason;
  status: ReportStatus;
  createdAt: string;
}

export interface DeleteReportResponse {
  reportId: number;
  cancelledAt: string;
}

export const createReport = (data: CreateReportRequest) =>
  axiosInstance.post<ApiResponse<CreateReportResponse>>("/api/v1/reports", data);

export const getMyReports = (page: number = 0, size: number = 20) =>
  axiosInstance.get<ApiResponse<PageResponse<MyReportItem>>>("/api/v1/reports/me", {
    params: { page, size },
  });

export const deleteReport = (reportId: number) =>
  axiosInstance.delete<ApiResponse<DeleteReportResponse>>(`/api/v1/reports/${reportId}`);
