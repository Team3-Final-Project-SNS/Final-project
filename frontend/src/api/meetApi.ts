import axiosInstance from "./axiosInstance";
import { ApiResponse } from "./authApi";

export interface PlaceVerificationResponse {
    matchId: number;
    verificationStatus: string;
    distanceMeters: number;
    authorPlaceVerifiedAt: string | null;
    applicantPlaceVerifiedAt: string | null;
    bothVerified: boolean;
}

export interface QrResponse {
    matchId: number;
    qrToken: string;
    expiresAt: string;
}

export interface QrScanResponse {
    matchId: number;
    verificationStatus: string;
    matchStatus: string;
    completedAt: string;
    refundedPoint: number;
}

export interface MeetVerificationResponse {
    matchId: number;
    verificationStatus: string;
    authorPlaceVerifiedAt: string | null;
    applicantPlaceVerifiedAt: string | null;
    qrIssuedToAuthor: boolean;
    qrExpiresAt: string | null;
    completedAt: string | null;
}

export type ExtensionStatus = "NONE" | "REQUESTED" | "ACCEPTED" | "REJECTED" | "EXPIRED";

export interface MeetExtensionResponse {
    matchId: number;
    extensionStatus: ExtensionStatus;
    requesterId: number | null;
    requesterNickname: string | null;
    isMyRequest?: boolean;
    originalMeetAt: string;
    expectedMeetAt: string;
    requestedAt: string | null;
    expiresAt: string | null;
}

export interface SingleLocationResponse {
    latitude: number;
    longitude: number;
    updatedAt: string;
}

export interface LocationResponse {
    myLocation: SingleLocationResponse;
    opponentLocation: SingleLocationResponse;
}

// GPS 장소 인증 API
export const createPlaceVerification = (matchId: number, data: { currentLat: number, currentLng: number }) => {
    return axiosInstance.post<ApiResponse<PlaceVerificationResponse>>(`/api/v1/matches/${matchId}/place-verification`, data);
};

// QR 토큰 발급/조회 API (등록자만 호출)
export const getMeetQr = (matchId: number) => {
    return axiosInstance.get<ApiResponse<QrResponse>>(`/api/v1/matches/${matchId}/qr`);
};

// QR 스캔 API (신청자만 호출)
export const createQrScan = (matchId: number, qrToken: string) => {
    return axiosInstance.post<ApiResponse<QrScanResponse>>(`/api/v1/matches/${matchId}/qr/scan`, {
        qrToken,
    });
};

// 인증 상태 조회 API
export const getMeetVerification = (matchId: number) => {
    return axiosInstance.get<ApiResponse<MeetVerificationResponse>>(`/api/v1/matches/${matchId}/verification`);
};

// 만남 시간 연장 요청
export const createMeetExtension = (matchId: number) => {
    return axiosInstance.post<ApiResponse<MeetExtensionResponse>>(`/api/v1/matches/${matchId}/extension/request`);
};

// 만남 시간 연장 상태 조회
export const getMeetExtension = (matchId: number) => {
    return axiosInstance.get<ApiResponse<MeetExtensionResponse>>(`/api/v1/matches/${matchId}/extension`);
};

// ────────────────────────────────────────
// 위치 정보 API
// ────────────────────────────────────────

// 내 위치 업데이트
export const updateMyLocation = (matchId: number, latitude: number, longitude: number) => {
    return axiosInstance.put<ApiResponse<{
        matchId: number,
        userId: number,
        latitude: number,
        longitude: number,
        updatedAt: string
    }>>(`/api/v1/matches/${matchId}/location`, {
        latitude,
        longitude,
    });
};

// 양측 위치 조회
export const getLocations = (matchId: number) => {
    return axiosInstance.get<ApiResponse<LocationResponse>>(`/api/v1/matches/${matchId}/location`);
};
