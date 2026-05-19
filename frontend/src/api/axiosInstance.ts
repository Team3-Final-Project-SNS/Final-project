import axios from "axios";

const axiosInstance = axios.create({
    baseURL: "http://localhost:8080",
    timeout: 5000,
    headers: {
        "Content-Type": "application/json",
    },
});

axiosInstance.interceptors.request.use((config) => {
    const userId = localStorage.getItem("userId") || "1";
    config.headers["userId"] = userId;
    return config;
});

axiosInstance.interceptors.response.use(
    (response) => response,
    (error) => {
        console.error("API 에러:", error.response?.data);
        return Promise.reject(error);
    }
);

export default axiosInstance;