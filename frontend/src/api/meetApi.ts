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

// ────────────────────────────────────────
// 위치 정보 API (9.1 / 9.2)
// ────────────────────────────────────────

// 9.1 내 위치 업데이트 - 1초마다 서버로 전송
// PUT /api/v1/matches/{matchId}/location
export const updateMyLocation = (matchId: number, latitude: number, longitude: number) => {
    return axiosInstance.put(`/api/v1/matches/${matchId}/location`, {
        latitude,
        longitude,
    });
};

// 9.2 양측 위치 조회 - 1초마다 폴링하여 상대방 위치 가져오기
// GET /api/v1/matches/{matchId}/location
export const getLocations = (matchId: number) => {
    return axiosInstance.get(`/api/v1/matches/${matchId}/location`);
};