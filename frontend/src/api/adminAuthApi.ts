import axiosInstance from "./axiosInstance";
import { ApiResponse } from "./authApi";

export type AdminRole = "SUPER" | "MANAGER";

export interface AdminLoginResponse {
  adminId: number;
  name: string;
  role: AdminRole;
  adminAccessToken: string;
}

export const adminLogin = (email: string, password: string) =>
  axiosInstance.post<ApiResponse<AdminLoginResponse>>("/api/v1/admin/auth/login", {
    email,
    password,
  });
