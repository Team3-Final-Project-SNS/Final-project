import axiosInstance from "./axiosInstance";

// GPS 장소 인증 API
export const createPlaceVerification = (matchId, currentLat, currentLng) => {
    return axiosInstance.post(`/api/v1/matches/${matchId}/place-verification`, {
        currentLat,
        currentLng,
    });
};

// QR 토큰 발급/조회 API (등록자만 호출)
export const getMeetQr = (matchId) => {
    return axiosInstance.get(`/api/v1/matches/${matchId}/qr`);
};

// QR 스캔 API (신청자만 호출)
export const createQrScan = (matchId, qrToken) => {
    return axiosInstance.post(`/api/v1/matches/${matchId}/qr/scan`, {
        qrToken,
    });
};

// 인증 상태 조회 API
export const getMeetVerification = (matchId) => {
    return axiosInstance.get(`/api/v1/matches/${matchId}/verification`);
};