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
    isRead: boolean;
    createdAt: string;
}

export interface CursorResponse<T> {
    content: T[];
    hasNext: boolean;
    nextCursor: number | null;
}

// 채팅방 목록 조회
export const getChatRooms = (isActive?: boolean) =>
    axiosInstance.get<ApiResponse<ChatRoomResponse[]>>("/api/v1/chat-rooms", {
        params: { isActive }
    });

// 메시지 목록 조회 (커서 기반 페이징)
export const getChatMessages = (chatRoomId: number, cursorId?: number, size: number = 50) =>
    axiosInstance.get<ApiResponse<CursorResponse<ChatMessageResponse>>>(`/api/v1/chat-rooms/${chatRoomId}/messages`, {
        params: { cursorId, size }
    });

// 채팅방 나가기
export const leaveChatRoom = (chatRoomId: number) =>
    axiosInstance.patch<ApiResponse<void>>(`/api/v1/chat-rooms/${chatRoomId}/leave`);
