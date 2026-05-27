import axiosInstance from "./axiosInstance";
import { ApiResponse } from "./authApi";

export interface RecommendedPost {
    postId: number;
    placeName: string;
    meetAt: string;
    deposit: number;
    reason: string;
    applicationAvailable: boolean;
    pointAffordable: boolean;
}

export interface MatchingChatResponse {
    conversationId: string | null;
    answer: string;
    recommendedPosts: RecommendedPost[];
    fallbackUsed: boolean;
}

export interface MatchingChatRequest {
    conversationId: string | null;
    message: string;
}

export const requestMatchingChat = (data: MatchingChatRequest) =>
    axiosInstance.post<ApiResponse<MatchingChatResponse>>("/api/v1/ai/matching/chat", data);
