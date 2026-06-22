import axiosInstance from './axiosInstance';
import { ApiResponse } from './authApi';
import { PageResponse, PostStatus } from './postApi';

export interface AdminPostItem {
  postId: number;
  authorNickname: string;
  placeName: string;
  content: string | null;
  meetAt: string;
  authorDeposit: number;
  status: PostStatus;
  createdAt: string;
  deleted: boolean;
  deletedAt: string | null;
}

export interface AdminPostDetail {
  postId: number;
  status: PostStatus;
  authorDeposit: number;
  content: string | null;
  placeName: string;
  meetAt: string;
  authorNickname: string;
  createdAt: string;
  deleted: boolean;
  deletedAt: string | null;
}

export interface AdminDeletePostResponse {
  postId: number;
  reportId: number | null;
  reason: string;
  refundedPoint: number;
  deletedAt: string;
}

export interface AdminRestorePostResponse {
  postId: number;
  redepositedPoint: number;
  restoredAt: string;
}

export const getAdminPosts = (
  universityId?: number,
  authorNickname?: string,
  status?: PostStatus,
  deleted?: boolean,
  keyword?: string,
  page: number = 0,
  size: number = 20,
) =>
  axiosInstance.get<ApiResponse<PageResponse<AdminPostItem>>>('/api/v1/admin/posts', {
    params: {
      universityId,
      authorNickname,
      status,
      deleted,
      keyword,
      page,
      size,
    },
  });

export const getAdminPost = (postId: number) =>
  axiosInstance.get<ApiResponse<AdminPostDetail>>(`/api/v1/admin/posts/${postId}`);

export const deleteAdminPost = (postId: number, reportId: number | null, reason: string) =>
  axiosInstance.delete<ApiResponse<AdminDeletePostResponse>>(`/api/v1/admin/posts/${postId}`, {
    data: {
      reportId,
      reason,
    },
  });

export const restoreAdminPost = (postId: number) =>
  axiosInstance.post<ApiResponse<AdminRestorePostResponse>>(`/api/v1/admin/posts/${postId}/restore`);
