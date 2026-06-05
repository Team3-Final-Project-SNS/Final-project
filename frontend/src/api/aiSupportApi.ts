import axiosInstance from './axiosInstance';
import { ApiResponse } from './authApi';

export type AiSupportCategory =
  | 'MATCH'
  | 'POST'
  | 'POINT'
  | 'CHAT'
  | 'REPORT'
  | 'ACCOUNT'
  | 'MEET'
  | 'REVIEW'
  | 'GENERAL';

export interface AiSupportChatResponse {
  conversationId: string;
  answer: string;
  category: AiSupportCategory;
  summary: string;
  actionRequired: boolean;
  fallbackUsed: boolean;
}

export const chatAiSupport = (message: string, conversationId?: string | null) =>
  axiosInstance.post<ApiResponse<AiSupportChatResponse>>('/api/v1/ai/support/chat', {
    conversationId,
    message,
  });
