import axiosInstance from "./axiosInstance";
import { ApiResponse } from "./authApi";
import { streamPost } from './sseStream';

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

const AI_MATCHING_CHAT_TIMEOUT_MS = 20000;

export const requestMatchingChat = (data: MatchingChatRequest) =>
    axiosInstance.post<ApiResponse<MatchingChatResponse>>(
        "/api/v1/ai/matching/chat",
        data,
        {
            timeout: AI_MATCHING_CHAT_TIMEOUT_MS,
        }
    );

export const streamMatchingChat = (
    data: MatchingChatRequest,
    onChunk: (chunk: string) => void
) =>
    streamPost({
        path: '/api/v1/ai/matching/chat/stream',
        body: data,
        onChunk,
    });

export const clearMatchingConversation = (conversationId: string) =>
    axiosInstance.delete(`/api/v1/ai/matching/chat/${conversationId}`);
