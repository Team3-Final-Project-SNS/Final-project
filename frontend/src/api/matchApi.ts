import axiosInstance from "./axiosInstance";
import { ApiResponse } from "./authApi";
import { PageResponse } from "./postApi";

export type MatchStatus = "MATCHED" | "COMPLETED" | "CANCELLED" | "HOST_NO_SHOW" | "GUEST_NO_SHOW" | "BOTH_NO_SHOW" | "DISPUTED";
export type DisputeType = "FUNERAL_CEREMONY" | "MEDICAL_EMERGENCY" | "PHONE_MALFUNCTION" | "GPS_ERROR" | "QR_ERROR" | "ADMIN_OVERRIDE";

export interface GetMatchesItemResponse {
    matchId: number;
    postId: number;
    opponentId: number;
    opponentNickname: string;
    opponentMajor: string;
    opponentStudentNumber: string;
    meetAt: string;
    placeName: string;
    currentApplicants: number;
    maxApplicants: number;
    myDeposit: number;
    isAuthor: boolean;
    status: MatchStatus;
    chatRoomId: number | null;
    matchedAt: string;
    completedAt: string | null;
}

export interface GetMatchResponse {
    matchId: number;
    postId: number;
    authorId: number;
    authorNickname: string;
    authorMajor: string;
    authorStudentNumber: string;
    applicantId: number;
    applicantNickname: string;
    applicantMajor: string;
    applicantStudentNumber: string;
    meetAt: string;
    placeName: string;
    placeLat: number;
    placeLng: number;
    authorDeposit: number;
    applicantDeposit: number;
    currentApplicants: number;
    maxApplicants: number;
    authorMannerTemperature: number | null;
    status: MatchStatus;
    chatRoomId: number | null;
    matchedAt: string;
    completedAt: string | null;
}

// 매칭 신청
export const createMatch = (postId: number) =>
    axiosInstance.post<ApiResponse<{ 
        matchId: number, 
        postId: number, 
        authorId: number, 
        applicantId: number,
        authorNickname: string,
        applicantNickname: string,
        authorDeposit: number,
        applicantDeposit: number,
        status: MatchStatus,
        chatRoomId: number | null,
        matchedAt: string
    }>>(`/api/v1/posts/${postId}/matches`);

// 매칭 취소
export interface MatchCancelResponse {
    matchId: number;
    status: MatchStatus;
    refundedPoint: number;
    forfeitedPoint: number;
}

export const updateMatchCancel = (matchId: number, reason?: string) =>
    axiosInstance.patch<ApiResponse<MatchCancelResponse>>(`/api/v1/matches/${matchId}/cancel`, { reason });

// 내 매칭 목록 조회
export const getMyMatches = (status?: MatchStatus, page: number = 0, size: number = 20) =>
    axiosInstance.get<ApiResponse<PageResponse<GetMatchesItemResponse>>>("/api/v1/matches/me", {
        params: { status, page, size }
    });

// 매칭 상세 조회
export const getMatchDetail = (matchId: number) =>
    axiosInstance.get<ApiResponse<GetMatchResponse>>(`/api/v1/matches/${matchId}`);

export interface CreateDisputeRequest {
    disputeType: DisputeType;
    reason: string;
}

export interface CreateDisputeResponse {
    disputeId: number;
    matchId: number;
    disputeType: DisputeType;
    status: string;
    submittedAt: string;
}

// 노쇼 이의제기 제출
export const createDispute = (matchId: number, request: CreateDisputeRequest) =>
    axiosInstance.post<ApiResponse<CreateDisputeResponse>>(`/api/v1/matches/${matchId}/disputes`, request);

export type DisputeStatus = "SUBMITTED" | "UNDER_REVIEW" | "ACCEPTED" | "PARTIALLY_ACCEPTED" | "REJECTED" | "HOLD";

export interface DisputeResponse {
    disputeId: number;
    matchId: number;
    disputeType: DisputeType;
    reason: string;
    status: DisputeStatus;
    adminComment: string | null;
    submittedAt: string;
    processedAt: string | null;
    holdDeadlineAt: string | null;
}

export interface NoShowMatchResponse {
    matchId: number;
    verificationStatus: string;
    noShowDecidedAt: string;
}

export const getMyNoShowMatches = () =>
    axiosInstance.get<ApiResponse<NoShowMatchResponse[]>>("/api/v1/matches/me/no-show");

export const getMyDispute = (matchId: number) =>
    axiosInstance.get<ApiResponse<DisputeResponse>>(`/api/v1/matches/${matchId}/disputes/me`);
