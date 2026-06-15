import axios from "axios";
import type { AxiosRequestConfig } from "axios";
import { toast } from "sonner";
import { clearAuthStatus } from "@/store/authStatusStore";

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

export const setAccessToken = (token: string) => {
    accessTokenMemory = token;
};
export const getAccessToken = () => accessTokenMemory;
export const clearAccessToken = () => {
    accessTokenMemory = null;
    clearAuthStatus();
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
    // HttpOnly 쿠키(refresh_token, device_id)를 요청에 자동으로 포함시키기 위해 필수
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
// [추가] 일반 유저 강제 로그아웃 공통 처리 함수
// - 멀티 디바이스 로그인으로 세션이 끊겼거나, refresh_token/device_id가
//   모두 무효화된 경우 호출됨
// ─────────────────────────────────────────────

// [추가] 무한 루프 방지용 플래그
// - 이미 강제 로그아웃 처리 중이면 중복 실행 차단
// - 풀 새로고침 대신 history API로 이동 → React 앱 재부트스트랩 방지
let isLoggingOut = false;

const forceLogoutToMain = (message?: string) => {
    // 이미 처리 중이면 무시 (무한 루프 핵심 방지 지점)
    if (isLoggingOut) {
        return;
    }
    isLoggingOut = true;

    clearAccessToken();
    sessionStorage.removeItem("accessToken");

    if (message) {
        toast.error(message);
    }

    // [수정] window.location.href (풀 새로고침) 대신
    // 현재 위치가 이미 "/"가 아닐 때만 이동
    // history.pushState + popstate 이벤트로 React Router에 알림
    // → 풀 새로고침 없이 라우트만 전환되어 무한루프 발생 안 함
    if (window.location.pathname !== "/") {
        window.history.pushState({}, "", "/");
        window.dispatchEvent(new PopStateEvent("popstate"));
    }

    // 플래그는 짧은 시간 뒤 해제 (다음 정상 401 처리를 위해)
    setTimeout(() => {
        isLoggingOut = false;
    }, 1000);
};

// ─────────────────────────────────────────────
// [추가] 관리자 강제 로그아웃 처리
// - 관리자는 refresh token이 없으므로(15분 Access Token만 사용)
//   재발급 시도 없이 즉시 로그아웃
// - sessionStorage에 저장된 관리자 정보 전부 삭제 후 메인 화면으로 이동
//   (관리자 전용 로그인 페이지로의 이동은 다른 처리와 충돌이 있어
//    일반 로그아웃과 동일하게 메인 화면으로 통일)
// ─────────────────────────────────────────────
let isAdminLoggingOut = false;

const forceAdminLogout = (message?: string) => {
    if (isAdminLoggingOut) {
        return;
    }
    isAdminLoggingOut = true;

    sessionStorage.removeItem("adminAccessToken");
    sessionStorage.removeItem("adminId");
    sessionStorage.removeItem("adminName");
    sessionStorage.removeItem("adminRole");

    if (message) {
        toast.error(message);
    }

    // 메인 화면으로 이동 (풀 새로고침으로 React 상태 완전 초기화)
    if (window.location.pathname !== "/") {
        window.location.replace("/");
    }

    setTimeout(() => {
        isAdminLoggingOut = false;
    }, 1000);
};

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

        // [핵심] 관리자 API에서 401 또는 403 발생 시 즉시 로그아웃 처리
        // - 401: 토큰 없음/만료
        // - 403: 토큰이 깨졌거나 유효하지 않을 때 Spring Security가 403을 반환하는 케이스 존재
        // - 관리자는 refresh token이 없으므로 두 경우 모두 재로그인이 유일한 해결책
        // - 메인 화면(/)으로 이동 후, 메인의 로그인 버튼을 통해 재로그인
        if ((error.response?.status === 401 || error.response?.status === 403) && isAdminEndpoint) {
            forceAdminLogout("세션이 만료되었습니다. 다시 로그인해주세요.");
            return Promise.reject(error);
        }

        if (
            error.response?.status === 401 &&
            !originalRequest._retry &&   // 이미 재시도한 요청 → 무한루프 방지
            !isAuthEndpoint &&           // 로그인/회원가입/refresh 자체의 401은 별도 처리
            !isAdminEndpoint &&          // 관리자는 위에서 이미 처리됨
            !isLoggingOut                // 이미 로그아웃 처리 중이면 재발급 시도 자체를 건너뜀
        ) {
            // 이미 재발급 중이면 → 대기열에 추가
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

                setAccessToken(newAccessToken);
                processQueue(null, newAccessToken);

                originalRequest.headers = {
                    ...originalRequest.headers,
                    Authorization: `Bearer ${newAccessToken}`,
                };
                return axiosInstance(originalRequest);

            } catch (refreshError) {
                if (axios.isAxiosError(refreshError)) {
                    console.error("[REFRESH 실패]", {
                        status: refreshError.response?.status,
                        data: refreshError.response?.data,
                        message: refreshError.message,
                    });
                } else {
                    console.error("[REFRESH 실패 - 알 수 없는 에러]", refreshError);
                }

                processQueue(refreshError, null);
                forceLogoutToMain("다른 기기에서 로그인되어 로그아웃되었습니다.");

                return Promise.reject(refreshError);
            } finally {
                isRefreshing = false;
            }
        }

        // refresh 요청 자체가 401인 경우
        if (error.response?.status === 401 && originalRequest.url?.includes("/api/v1/auth/refresh")) {
            console.error("[REFRESH 요청 자체가 401]", error.response?.data);
            forceLogoutToMain("다른 기기에서 로그인되어 로그아웃되었습니다.");
            return Promise.reject(error);
        }

        const errorCode = error.response?.data?.code;
        if (error.response?.status === 403 && (errorCode === "SUSPENDED_001" || errorCode === "SUSPENDED_002")) {
            toast.error("정지된 계정입니다. 문의하기로 이의를 제기해 주세요.");
        }

        console.error("API 에러:", error.response?.data);
        return Promise.reject(error);
    }
);

export default axiosInstance;