import axios from "axios";
import type { AxiosRequestConfig } from "axios";

// ─────────────────────────────────────────────
// 토큰 없이 접근 가능한 공개 엔드포인트 목록
// ─────────────────────────────────────────────
const PUBLIC_ENDPOINTS = [
    "/api/v1/auth/login",
    "/api/v1/auth/signup",
    "/api/v1/auth/email/otp",
    "/api/v1/auth/email/otp/verify",
    "/api/v1/auth/refresh",
    "/api/v1/admin/auth/login",
    "/api/v1/universities",
];

// ─────────────────────────────────────────────
// [수정] 액세스 토큰 저장소를 sessionStorage → 메모리 변수로 변경
//
// 기존 sessionStorage 문제점:
//   - 새로고침하면 초기화됨 → 매번 재발급 요청 발생
//   - ChatPage에서 sessionStorage로 토큰 꺼내 웹소켓 연결 → 새로고침 후 null → 연결 불가
//
// 메모리 변수 장점:
//   - 탭이 살아있는 동안 유지
//   - 새로고침 시 초기화되지만 → HttpOnly 쿠키의 리프레시 토큰으로 자동 재발급됨
// ─────────────────────────────────────────────
let accessTokenMemory: string | null = null;

// 외부에서 토큰을 저장/조회/삭제하는 헬퍼 함수
// LoginPage, ChatPage 등에서 import해서 사용
export const setAccessToken = (token: string) => {
    accessTokenMemory = token;
};
export const getAccessToken = () => accessTokenMemory;
export const clearAccessToken = () => {
    accessTokenMemory = null;
};

// ─────────────────────────────────────────────
// axios 인스턴스 생성
// ─────────────────────────────────────────────
const axiosInstance = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || "http://localhost:8080",
    // [수정] 5000ms → 15000ms
    // 이유: GPS 인증, QR 인증 등 처리가 긴 요청이 5초 안에 못 끝나면
    //       타임아웃 에러가 나서 먹통처럼 보임
    timeout: 15000,
    headers: {
        "Content-Type": "application/json",
        "ngrok-skip-browser-warning": "true",
    },
    // HttpOnly 쿠키(refresh_token)를 요청에 자동으로 포함시키기 위해 필수
    withCredentials: true,
});

// ─────────────────────────────────────────────
// 토큰 재발급 중 대기 중인 요청들을 담는 큐
// 재발급 완료 후 한꺼번에 재시도함
// ─────────────────────────────────────────────
let isRefreshing = false;
let failedQueue: {
    resolve: (token: string) => void;
    reject: (error: unknown) => void;
}[] = [];

// 큐에 쌓인 요청들을 일괄 처리
// 재발급 성공 → 새 토큰으로 resolve / 실패 → reject
const processQueue = (error: unknown, token: string | null) => {
    failedQueue.forEach(({ resolve, reject }) => {
        if (error) {
            reject(error);
        } else {
            resolve(token!);
        }
    });
    failedQueue = [];
};

// ─────────────────────────────────────────────
// 요청 인터셉터
// 서버로 나가기 직전에 실행 → Authorization 헤더 주입
// ─────────────────────────────────────────────
axiosInstance.interceptors.request.use((config) => {
    const isPublic = PUBLIC_ENDPOINTS.some((endpoint) =>
        config.url?.includes(endpoint)
    );

    if (!isPublic) {
        const isAdminEndpoint = config.url?.includes("/api/v1/admin");

        if (isAdminEndpoint) {
            // 관리자 토큰은 sessionStorage 유지
            // 이유: 관리자는 리프레시 토큰 없이 재로그인 강제하는 정책 (기획서 명시)
            const adminToken = sessionStorage.getItem("adminAccessToken");
            if (adminToken) {
                config.headers["Authorization"] = `Bearer ${adminToken}`;
            }
        } else {
            // [수정] 일반 유저는 메모리에서 토큰 꺼냄
            const token = getAccessToken();
            if (token) {
                config.headers["Authorization"] = `Bearer ${token}`;
            }
        }
    }

    return config;
});

// ─────────────────────────────────────────────
// 응답 인터셉터
// 401 응답 시 자동으로 토큰 재발급 시도
// ─────────────────────────────────────────────
axiosInstance.interceptors.response.use(
    (response) => response,

    async (error) => {
        const originalRequest = error.config as AxiosRequestConfig & {
            _retry?: boolean;
        };

        const isAuthEndpoint = PUBLIC_ENDPOINTS.some((endpoint) =>
            originalRequest.url?.includes(endpoint)
        );
        const isAdminEndpoint = originalRequest.url?.includes("/api/v1/admin");

        if (
            error.response?.status === 401 &&
            !originalRequest._retry &&   // 이미 재시도한 요청 → 무한루프 방지
            !isAuthEndpoint &&           // 로그인/회원가입 401은 재발급 시도 안 함
            !isAdminEndpoint             // 관리자는 리프레시 없이 재로그인 강제 (기획서 정책)
        ) {
            // 이미 재발급 중이면 → 대기열에 추가
            // 재발급 완료되면 새 토큰 받아서 원래 요청 재시도
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
                // 재발급 요청 (리프레시 토큰은 HttpOnly 쿠키로 자동 전송)
                const { data } = await axiosInstance.post("/api/v1/auth/refresh");
                const newAccessToken = data.data.accessToken;

                // [수정] sessionStorage 대신 메모리에 저장
                setAccessToken(newAccessToken);

                // 대기 중이던 요청들도 새 토큰으로 재시도
                processQueue(null, newAccessToken);

                originalRequest.headers = {
                    ...originalRequest.headers,
                    Authorization: `Bearer ${newAccessToken}`,
                };
                return axiosInstance(originalRequest);

            } catch (refreshError) {
                // 리프레시 토큰도 만료 → 강제 로그아웃
                processQueue(refreshError, null);
                clearAccessToken();
                sessionStorage.removeItem("accessToken"); // 혹시 남아있는 구버전 토큰 정리
                window.location.href = "/login";
                return Promise.reject(refreshError);
            } finally {
                isRefreshing = false;
            }
        }

        console.error("API 에러:", error.response?.data);
        return Promise.reject(error);
    }
);

export default axiosInstance;