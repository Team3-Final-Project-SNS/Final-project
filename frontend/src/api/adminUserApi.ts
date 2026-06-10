import axiosInstance from "./axiosInstance";
import { ApiResponse } from "./authApi";
import { PageResponse } from "./postApi";

export type AdminUserStatus = "ACTIVE" | "SUSPENDED" | "WITHDRAWN";

export interface AdminUserItem {
  userId: number;
  email: string;
  name: string;
  nickname: string;
  universityName: string;
  point: number;
  mannerTemperature: number;
  status: AdminUserStatus;
  createdAt: string;
}

export interface AdminSuspendUserResponse {
  userId: number;
  status: AdminUserStatus;
  reason: string;
  suspendedAt: string;
}

export interface AdminReinstateUserResponse {
  userId: number;
  status: AdminUserStatus;
  reason: string;
  reinstatedAt: string;
}

export const getAdminUsers = (
  status?: AdminUserStatus,
  keyword?: string,
  page: number = 0,
  size: number = 100,
) =>
  axiosInstance.get<ApiResponse<PageResponse<AdminUserItem>>>("/api/v1/admin/users", {
    params: { status, keyword, page, size },
  });

export const suspendAdminUser = (userId: number, reason: string) =>
  axiosInstance.patch<ApiResponse<AdminSuspendUserResponse>>(`/api/v1/admin/users/${userId}/suspend`, {
    reason,
  });

export const reinstateAdminUser = (userId: number, reason: string) =>
  axiosInstance.patch<ApiResponse<AdminReinstateUserResponse>>(`/api/v1/admin/users/${userId}/reinstate`, {
    reason,
  });
