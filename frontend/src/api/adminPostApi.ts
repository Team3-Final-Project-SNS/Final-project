import axiosInstance from './axiosInstance';
import { ApiResponse } from './authApi';
import { PageResponse, PostStatus } from './postApi';

export interface AdminPostItem {
  postId: number;
  authorNickname: string;
  placeName: string;
  meetAt: string;
  authorDeposit: number;
  status: PostStatus;
  createdAt: string;
}

export interface AdminDeletePostResponse {
  postId: number;
  reportId: number;
  reason: string;
  refundedPoint: number;
  deletedAt: string;
}

export const getAdminPosts = (
  universityId?: number,
  status?: PostStatus,
  keyword?: string,
  page: number = 0,
  size: number = 20,
) =>
  axiosInstance.get<ApiResponse<PageResponse<AdminPostItem>>>('/api/v1/admin/posts', {
    params: {
      universityId,
      status,
      keyword,
      page,
      size,
    },
  });

export const deleteAdminPost = (postId: number, reportId: number, reason: string) =>
  axiosInstance.delete<ApiResponse<AdminDeletePostResponse>>(`/api/v1/admin/posts/${postId}`, {
    data: {
      reportId,
      reason,
    },
  });
