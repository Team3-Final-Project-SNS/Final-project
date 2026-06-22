import axiosInstance from "./axiosInstance";
import { ApiResponse } from "./authApi";

export interface GetUserResponse {
    userId: number;
    email: string;
    name: string;
    nickname: string;
    universityId: number;
    major: string;
    studentNumber: string;
    birthDate: string;
    gender: "MALE" | "FEMALE";
    point: number;
    mannerTemperature: number;
    status: "ACTIVE" | "SUSPENDED" | "WITHDRAWN";
    createdAt: string;
}

export interface UpdateUserRequest {
    currentPassword?: string;
    newPassword?: string;
    nickname?: string;
    major?: string;
}

export interface UpdateUserResponse {
    userId: number;
    nickname: string;
    major: string;
    passwordChanged: boolean;
    updatedAt: string;
}

export interface WithdrawUserRequest {
    password: string;
}

export interface WithdrawUserResponse {
    userId: number;
    withdrawnAt: string;
}

// 내 정보 조회
export const getUserMe = () =>
    axiosInstance.get<ApiResponse<GetUserResponse>>("/api/v1/users/me");

// 내 정보 수정
export const updateUserMe = (data: UpdateUserRequest) =>
    axiosInstance.patch<ApiResponse<UpdateUserResponse>>("/api/v1/users/me", data);

// 회원 탈퇴
export const withdrawUserMe = (data: WithdrawUserRequest) =>
    axiosInstance.delete<ApiResponse<WithdrawUserResponse>>("/api/v1/users/me", { data });
