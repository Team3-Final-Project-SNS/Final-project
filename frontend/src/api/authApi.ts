import axiosInstance from "./axiosInstance";

// 로그인 — POST /api/v1/auth/login
export const login = (email: string, password: string) =>
    axiosInstance.post("/api/v1/auth/login", { email, password });

// 로그아웃 — POST /api/v1/auth/logout
export const logout = () =>
    axiosInstance.post("/api/v1/auth/logout");