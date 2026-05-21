import axiosInstance from "./axiosInstance";
import { ApiResponse } from "./authApi";
import { PageResponse } from "./postApi";

export type MatchStatus = "MATCHED" | "COMPLETED" | "CANCELLED" | "AUTHOR_NO_SHOW" | "APPLICANT_NO_SHOW" | "BOTH_NO_SHOW" | "DISPUTED";

export interface GetMatchesItemResponse {
    matchId: number;
    postId: number;
    opponentId: number;
    opponentNickname: string;
    opponentMajor: string;
    opponentStudentNumber: string;
    meetAt: string;
    placeName: string;
    myDeposit: number;
    status: MatchStatus;
    chatRoomId: number;
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
    status: MatchStatus;
    chatRoomId: number;
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
        status: MatchStatus,
        chatRoomId: number,
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
