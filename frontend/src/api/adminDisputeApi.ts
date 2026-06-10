import axiosInstance from './axiosInstance';
import { ApiResponse } from './authApi';
import { PageResponse } from './postApi';

export type DisputeStatus =
  | 'SUBMITTED'
  | 'UNDER_REVIEW'
  | 'ACCEPTED'
  | 'PARTIALLY_ACCEPTED'
  | 'REJECTED'
  | 'HOLD';

export type DisputeType =
  | 'FUNERAL_CEREMONY'
  | 'MEDICAL_EMERGENCY'
  | 'PHONE_MALFUNCTION'
  | 'GPS_ERROR'
  | 'QR_ERROR'
  | 'ADMIN_OVERRIDE';

export interface AdminDisputeItem {
  disputeId: number;
  matchId: number;
  applicantNickname: string;
  reason: string;
  status: DisputeStatus;
  submittedAt: string;
}

export interface AdminNoShowCandidate {
  matchId: number;
  verificationStatus: string;
  hostNickname: string;
  guestNickname: string;
  meetAt: string;
  hasDispute: boolean;
  disputeDeadline: string | null;
}

export interface AdminDisputeDetail {
  disputeId: number;
  matchId: number;
  applicantNickname: string;
  disputeType: DisputeType;
  reason: string;
  status: DisputeStatus;
  verificationStatus: string;
  authorPlaceVerifiedAt: string | null;
  applicantPlaceVerifiedAt: string | null;
  submittedAt: string;
  chatMessages: {
    senderId: number;
    senderNickname: string;
    content: string;
    createdAt: string;
  }[];
}

export const getAdminDisputes = (
  status?: DisputeStatus,
  page: number = 0,
  size: number = 20,
) =>
  axiosInstance.get<ApiResponse<PageResponse<AdminDisputeItem>>>('/api/v1/admin/disputes', {
    params: { status, page, size },
  });

export const getAdminNoShowCandidates = (page: number = 0, size: number = 20) =>
  axiosInstance.get<ApiResponse<PageResponse<AdminNoShowCandidate>>>('/api/v1/admin/no-show-candidates', {
    params: { page, size },
  });

export const getAdminDispute = (disputeId: number) =>
  axiosInstance.get<ApiResponse<AdminDisputeDetail>>(`/api/v1/admin/disputes/${disputeId}`);

export const judgeAdminDispute = (
  disputeId: number,
  status: Extract<DisputeStatus, 'ACCEPTED' | 'PARTIALLY_ACCEPTED' | 'REJECTED' | 'HOLD'>,
  comment: string,
) =>
  axiosInstance.patch(`/api/v1/admin/disputes/${disputeId}/judge`, { status, comment });
