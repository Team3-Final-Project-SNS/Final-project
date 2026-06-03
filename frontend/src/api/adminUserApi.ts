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

export const getAdminUsers = (
  status?: AdminUserStatus,
  keyword?: string,
  page: number = 0,
  size: number = 100,
) =>
  axiosInstance.get<ApiResponse<PageResponse<AdminUserItem>>>("/api/v1/admin/users", {
    params: { status, keyword, page, size },
  });
