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
const MATCHING_RECOMMENDATIONS_MARKER = '__MATCHING_RECOMMENDATIONS__';

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
): Promise<MatchingChatResponse> => {
    let metadataStarted = false;

    return streamPost({
        path: '/api/v1/ai/matching/chat/stream',
        body: data,
        onChunk: (chunk) => {
            if (metadataStarted) {
                return;
            }

            if (chunk.includes(MATCHING_RECOMMENDATIONS_MARKER)) {
                const [visibleChunk] = chunk.split(MATCHING_RECOMMENDATIONS_MARKER);
                metadataStarted = true;
                if (visibleChunk) {
                    onChunk(visibleChunk);
                }
                return;
            }

            onChunk(chunk);
        },
    }).then((rawAnswer) => {
        const markerIndex = rawAnswer.indexOf(MATCHING_RECOMMENDATIONS_MARKER);
        if (markerIndex < 0) {
            return {
                conversationId: data.conversationId,
                answer: rawAnswer,
                recommendedPosts: [],
                fallbackUsed: false,
            };
        }

        const answer = rawAnswer.slice(0, markerIndex);
        const metadata = rawAnswer.slice(markerIndex + MATCHING_RECOMMENDATIONS_MARKER.length);

        return {
            conversationId: data.conversationId,
            answer,
            recommendedPosts: parseRecommendedPosts(metadata),
            fallbackUsed: false,
        };
    });
};

function parseRecommendedPosts(metadata: string): RecommendedPost[] {
    try {
        const parsed = JSON.parse(metadata);
        return Array.isArray(parsed) ? parsed : [];
    } catch {
        return [];
    }
}

export const clearMatchingConversation = (conversationId: string) =>
    axiosInstance.delete(`/api/v1/ai/matching/chat/${conversationId}`);
