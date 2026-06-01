import axiosInstance from "./axiosInstance";
import { ApiResponse } from "./authApi";
import { PageResponse } from "./postApi";

export type InquiryType = "ACCOUNT" | "HISTORY" | "MATCH" | "OTHER" | "PAYMENT" | "REPORT" | "USAGE";
export type InquiryAnswerStatus = "PENDING" | "READ" | "ANSWERED" | "WITHDRAWN";

export interface CreateInquiryRequest {
  title: string;
  content: string;
  type: InquiryType;
}

export interface CreateInquiryResponse {
  inquiryId: number;
  status: InquiryAnswerStatus;
  createdAt: string;
}

export interface InquiryListItem {
  inquiryId: number;
  title: string;
  type: InquiryType;
  answerStatus: InquiryAnswerStatus;
  createdAt: string;
}

export interface InquiryAnswer {
  adminName: string;
  content: string;
  createdAt: string;
}

export interface InquiryDetail {
  inquiryId: number;
  title: string;
  content: string;
  type: InquiryType;
  answerStatus: InquiryAnswerStatus;
  answer: InquiryAnswer | null;
  createdAt: string;
}

export interface CancelInquiryResponse {
  inquiryId: number;
  cancelledAt: string;
}

export const createInquiry = (data: CreateInquiryRequest) =>
  axiosInstance.post<ApiResponse<CreateInquiryResponse>>("/api/v1/inquiries", data);

export const getMyInquiries = (page: number = 0, size: number = 20) =>
  axiosInstance.get<ApiResponse<PageResponse<InquiryListItem>>>("/api/v1/inquiries/me", {
    params: { page, size },
  });

export const getInquiry = (inquiryId: number) =>
  axiosInstance.get<ApiResponse<InquiryDetail>>(`/api/v1/inquiries/${inquiryId}`);

export const cancelInquiry = (inquiryId: number) =>
  axiosInstance.delete<ApiResponse<CancelInquiryResponse>>(`/api/v1/inquiries/${inquiryId}`);
