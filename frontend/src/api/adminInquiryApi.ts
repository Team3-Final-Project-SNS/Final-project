import axiosInstance from "./axiosInstance";
import { ApiResponse } from "./authApi";
import { PageResponse } from "./postApi";
import { InquiryAnswerStatus, InquiryType } from "./inquiryApi";

export interface AdminInquiryItem {
  inquiryId: number;
  userNickname: string;
  title: string;
  type: InquiryType;
  answerStatus: InquiryAnswerStatus;
  createdAt: string;
}

export interface AdminInquiryDetail {
  inquiryId: number;
  userNickname: string;
  userEmail: string;
  universityName: string;
  title: string;
  content: string;
  type: InquiryType;
  answerStatus: InquiryAnswerStatus;
  answer: {
    answerId: number;
    adminName: string;
    content: string;
    createdAt: string;
  } | null;
  createdAt: string;
  updatedAt: string;
}

export interface AdminCreateInquiryAnswerResponse {
  answerId: number;
  inquiryId: number;
  adminName: string;
  content: string;
  createdAt: string;
}

export const getAdminInquiries = (
  status?: InquiryAnswerStatus,
  type?: InquiryType,
  page: number = 0,
  size: number = 20,
) =>
  axiosInstance.get<ApiResponse<PageResponse<AdminInquiryItem>>>("/api/v1/admin/inquiries", {
    params: { status, type, page, size },
  });

export const getAdminInquiry = (inquiryId: number) =>
  axiosInstance.get<ApiResponse<AdminInquiryDetail>>(`/api/v1/admin/inquiries/${inquiryId}`);

export const answerAdminInquiry = (inquiryId: number, content: string) =>
  axiosInstance.post<ApiResponse<AdminCreateInquiryAnswerResponse>>(`/api/v1/admin/inquiries/${inquiryId}/answers`, {
    content,
  });
