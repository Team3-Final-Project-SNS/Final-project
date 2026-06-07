import axiosInstance from './axiosInstance';
import { ApiResponse } from './authApi';
import { ReportReason } from './reportApi';
import { streamPost } from './sseStream';

export type AiReportChatAction = 'ANALYZE_REPORT' | 'HIGH_RISK_USERS' | 'CLARIFY' | 'GENERAL_GUIDE';
export type AiReportRiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type AiReportDecisionSuggestion = 'ACCEPT' | 'REJECT' | 'NEEDS_REVIEW';

export interface AiReportAnalysis {
  summaryId: number;
  reportId: number;
  reportReason: ReportReason;
  decisionSuggestion: AiReportDecisionSuggestion;
  riskLevel: AiReportRiskLevel;
  summary: string;
  evidence: string;
  recommendationReason: string;
  actionGuide: string;
  confidenceScore: number;
  needsAdminReview: boolean;
  fallbackUsed: boolean;
  createdAt: string;
}

export interface AiReportHighRiskUser {
  userId: number;
  nickname: string;
  riskLevel: AiReportRiskLevel;
  totalReportCount: number;
  pendingReportCount: number;
  acceptedReportCount: number;
  reasonSummary: string;
  recommendedAction: string;
  relatedReportIds: number[];
}

export interface AiReportHighRiskUsers {
  answer: string;
  highRiskUsers: AiReportHighRiskUser[];
  fallbackUsed: boolean;
}

export interface AiReportChatResponse {
  answer: string;
  action: AiReportChatAction;
  reportAnalysis: AiReportAnalysis | null;
  highRiskUsers: AiReportHighRiskUsers | null;
  fallbackUsed: boolean;
}

export const chatAiReport = (message: string) =>
  axiosInstance.post<ApiResponse<AiReportChatResponse>>('/api/v1/admin/ai/reports/chat', {
    message,
  });

export const streamAiReport = (
  message: string,
  conversationId: string | null | undefined,
  onChunk: (chunk: string) => void,
) =>
  streamPost({
    path: '/api/v1/admin/ai/reports/chat/stream',
    body: {
      conversationId,
      message,
    },
    admin: true,
    onChunk,
  });
