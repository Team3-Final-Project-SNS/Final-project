import axios from "axios";

const axiosInstance = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || "http://localhost:8080",
    timeout: 5000,
    headers: {
        "Content-Type": "application/json",
        "ngrok-skip-browser-warning": "true",
    },
    // refresh_token 쿠키 자동 전송
    withCredentials: true,
});

// 요청 인터셉터 — localStorage에서 accessToken 꺼내서 Authorization 헤더에 주입
axiosInstance.interceptors.request.use((config) => {
    const accessToken = localStorage.getItem("accessToken");
    if (accessToken) {
        config.headers["Authorization"] = `Bearer ${accessToken}`;
    }
    return config;
});

// 응답 인터셉터 — 에러 로깅
axiosInstance.interceptors.response.use(
    (response) => response,
    (error) => {
        console.error("API 에러:", error.response?.data);
        return Promise.reject(error);
    }
);

export default axiosInstance;