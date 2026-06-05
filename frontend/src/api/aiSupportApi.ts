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

const AI_SUPPORT_CHAT_TIMEOUT_MS = 20000;

export const chatAiSupport = (message: string, conversationId?: string | null) =>
  axiosInstance.post<ApiResponse<AiSupportChatResponse>>(
    '/api/v1/ai/support/chat',
    {
      conversationId,
      message,
    },
    {
      timeout: AI_SUPPORT_CHAT_TIMEOUT_MS,
    },
  );
