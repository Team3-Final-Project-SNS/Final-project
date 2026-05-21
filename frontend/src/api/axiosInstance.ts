import axios from "axios";
import type { AxiosRequestConfig } from "axios";

// ─────────────────────────────────────────────
// 토큰 없이 접근 가능한 공개 엔드포인트 목록
// 이 경로들은 요청 인터셉터에서 Authorization 헤더를 붙이지 않음
// ─────────────────────────────────────────────
const PUBLIC_ENDPOINTS = [
    "/api/v1/auth/login",
    "/api/v1/auth/signup",
    "/api/v1/auth/email/otp",
    "/api/v1/auth/email/otp/verify",
    "/api/v1/auth/refresh",
];

// ─────────────────────────────────────────────
// axios 인스턴스 생성
// baseURL, timeout, 기본 헤더 설정
// ─────────────────────────────────────────────
const axiosInstance = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || "http://localhost:8080",
    timeout: 5000,
    headers: {
        "Content-Type": "application/json",
        "ngrok-skip-browser-warning": "true",
    },
    // HttpOnly 쿠키(refresh_token)를 요청에 자동으로 포함시키기 위해 필요
    withCredentials: true,
});

// ─────────────────────────────────────────────
// 토큰 재발급 중 들어온 요청들을 담아두는 큐
// 재발급 완료 후 한꺼번에 재시도함
// ─────────────────────────────────────────────
let isRefreshing = false; // 현재 재발급 진행 중인지 여부
let failedQueue: {
    resolve: (token: string) => void;
    reject: (error: unknown) => void;
}[] = [];

// 큐에 쌓인 요청들을 일괄 처리하는 함수
// 재발급 성공 시 → 새 토큰으로 resolve / 실패 시 → reject
const processQueue = (error: unknown, token: string | null) => {
    failedQueue.forEach(({ resolve, reject }) => {
        if (error) {
            reject(error);
        } else {
            resolve(token!);
        }
    });
    failedQueue = []; // 처리 후 큐 비우기
};

// ─────────────────────────────────────────────
// 요청 인터셉터
// 요청이 서버로 나가기 직전에 실행됨
// ─────────────────────────────────────────────
axiosInstance.interceptors.request.use((config) => {
    // 현재 요청 URL이 공개 엔드포인트인지 확인
    const isPublic = PUBLIC_ENDPOINTS.some((endpoint) =>
        config.url?.includes(endpoint)
    );

    // 공개 엔드포인트가 아닌 경우에만 Authorization 헤더 추가
    if (!isPublic) {
        const accessToken = localStorage.getItem("accessToken");
        if (accessToken) {
            config.headers["Authorization"] = `Bearer ${accessToken}`;
        }
    }

    return config;
});

// ─────────────────────────────────────────────
// 응답 인터셉터
// ─────────────────────────────────────────────
axiosInstance.interceptors.response.use(
    (response) => response,

    async (error) => {
        const originalRequest = error.config as AxiosRequestConfig & {
            _retry?: boolean;
        };

        // 401 에러이고, 재시도 안 했고, 인증 관련 엔드포인트가 아닌 경우에만 재발급 시도
        // → 로그인/회원가입 실패 401은 재발급 시도하지 않음
        const isAuthEndpoint = PUBLIC_ENDPOINTS.some((endpoint) =>
            originalRequest.url?.includes(endpoint)
        );

        if (
            error.response?.status === 401 &&
            !originalRequest._retry &&
            !isAuthEndpoint  // ← 핵심: 공개 엔드포인트 401은 재발급 안 함
        ) {
            if (isRefreshing) {
                return new Promise((resolve, reject) => {
                    failedQueue.push({ resolve, reject });
                }).then((token) => {
                    originalRequest.headers = {
                        ...originalRequest.headers,
                        Authorization: `Bearer ${token}`,
                    };
                    return axiosInstance(originalRequest);
                });
            }

            originalRequest._retry = true;
            isRefreshing = true;

            try {
                const { data } = await axiosInstance.post("/api/v1/auth/refresh");
                const newAccessToken = data.data.accessToken;
                localStorage.setItem("accessToken", newAccessToken);
                processQueue(null, newAccessToken);
                originalRequest.headers = {
                    ...originalRequest.headers,
                    Authorization: `Bearer ${newAccessToken}`,
                };
                return axiosInstance(originalRequest);
            } catch (refreshError) {
                processQueue(refreshError, null);
                localStorage.removeItem("accessToken");
                window.location.href = "/login";
                return Promise.reject(refreshError);
            } finally {
                isRefreshing = false;
            }
        }

        // 그 외 에러(로그인 실패 401 포함)는 그대로 던져서 호출부에서 처리
        console.error("API 에러:", error.response?.data);
        return Promise.reject(error);
    }
);

export default axiosInstance;