import axiosInstance from "./axiosInstance";
import { ApiResponse } from "./authApi";

export interface ChatRoomResponse {
    chatRoomId: number;
    matchId: number;
    opponentId: number;
    opponentNickname: string;
    lastMessage: string;
    lastMessageAt: string;
    unreadCount: number;
    createdAt: string;
}

export interface ChatMessageResponse {
    messageId: number;
    senderId: number;
    senderNickname: string;
    content: string;
    systemMessage: boolean;
    isRead: boolean;
    createdAt: string;
}

export interface ChatMemberResponse {
    userId: number;
    nickname: string;
    joinedAt: string;
}

export interface CursorResponse<T> {
    content: T[];
    hasNext: boolean;
    nextCursor: number | null;
}

export const getChatRooms = (isActive?: boolean) =>
    axiosInstance.get<ApiResponse<ChatRoomResponse[]>>("/api/v1/chat-rooms", {
        params: { isActive }
    });

export const getChatMessages = (chatRoomId: number, cursorId?: number, size: number = 50) =>
    axiosInstance.get<ApiResponse<CursorResponse<ChatMessageResponse>>>(`/api/v1/chat-rooms/${chatRoomId}/messages`, {
        params: { cursorId, size }
    });

export const getChatMembers = (chatRoomId: number) =>
    axiosInstance.get<ApiResponse<ChatMemberResponse[]>>(`/api/v1/chat-rooms/${chatRoomId}/members`);

export const leaveChatRoom = (chatRoomId: number) =>
    axiosInstance.patch<ApiResponse<void>>(`/api/v1/chat-rooms/${chatRoomId}/leave`);
