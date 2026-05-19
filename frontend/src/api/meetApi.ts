import axiosInstance from "./axiosInstance";

// GPS 장소 인증 API
export const createPlaceVerification = (matchId: number, currentLat: number, currentLng: number) => {
    return axiosInstance.post(`/api/v1/matches/${matchId}/place-verification`, {
        currentLat,
        currentLng,
    });
};

// QR 토큰 발급/조회 API (등록자만 호출)
export const getMeetQr = (matchId: number) => {
    return axiosInstance.get(`/api/v1/matches/${matchId}/qr`);
};

// QR 스캔 API (신청자만 호출)
export const createQrScan = (matchId: number, qrToken: string) => {
    return axiosInstance.post(`/api/v1/matches/${matchId}/qr/scan`, {
        qrToken,
    });
};

// 인증 상태 조회 API
export const getMeetVerification = (matchId: number) => {
    return axiosInstance.get(`/api/v1/matches/${matchId}/verification`);
};