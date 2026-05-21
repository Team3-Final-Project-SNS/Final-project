import axiosInstance from "./axiosInstance";

// ─────────────────────────────────────────────
// 이메일 OTP 관련
// ─────────────────────────────────────────────

// OTP 인증번호 이메일 발송
export const sendEmailOtp = (email: string) =>
    axiosInstance.post("/api/v1/auth/email/otp", { email });

// OTP 검증 및 signup_token 발급
export interface OtpVerifyResponse {
    universityId: number;
    universityName: string;
}

export const verifyEmailOtp = (email: string, otpCode: string) =>
    axiosInstance.post<ApiResponse<OtpVerifyResponse>>("/api/v1/auth/email/otp/verify", { email, otpCode });

// ─────────────────────────────────────────────
// 회원가입 및 인증
// ─────────────────────────────────────────────

export interface TermAgreement {
    termVersion: string;
    agreed: boolean;
}

// 회원가입
export interface SignupRequest {
    password: string;
    name: string;
    nickname: string;
    birthDate: string; // spec says birthDate, backend says birthDate
    gender: "MALE" | "FEMALE";
    major: string;
    studentNumber: string;
    termAgreements: TermAgreement[];
}

export const signup = (data: SignupRequest) =>
    axiosInstance.post("/api/v1/auth/signup", data);

// 로그인 — POST /api/v1/auth/login
export const login = (email: string, password: string) =>
    axiosInstance.post("/api/v1/auth/login", { email, password });

// 로그아웃 — POST /api/v1/auth/logout
export const logout = () =>
    axiosInstance.post("/api/v1/auth/logout");

// 토큰 재발급 — POST /api/v1/auth/refresh
export const refresh = () =>
    axiosInstance.post("/api/v1/auth/refresh");

// ─────────────────────────────────────────────
// 공통 응답 타입
// ─────────────────────────────────────────────
export interface ApiResponse<T> {
    success: boolean;
    code: string;
    message: string;
    data: T;
}
