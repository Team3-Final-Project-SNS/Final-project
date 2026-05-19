import axios from "axios";

// axios 인스턴스 생성 - 공통 설정을 한 곳에서 관리
const axiosInstance = axios.create({
    // 모든 요청의 기본 URL
    baseURL: "http://localhost:8080",

    // 요청 타임아웃 5초
    timeout: 5000,

    headers: {
        "Content-Type": "application/json",
    },
});

// 요청 인터셉터 - 모든 요청이 나가기 전에 실행
// TODO: JWT 도입 후 Authorization: Bearer {token} 으로 교체
axiosInstance.interceptors.request.use((config) => {
    // 임시: localStorage에서 userId 꺼내서 헤더에 추가
    const userId = localStorage.getItem("userId") || "1";
    config.headers["userId"] = userId;
    return config;
});

// 응답 인터셉터
axiosInstance.interceptors.response.use(
    (response) => response,
    (error) => {
        console.error("API 에러:", error.response?.data);
        return Promise.reject(error);
    }
);

export default axiosInstance;