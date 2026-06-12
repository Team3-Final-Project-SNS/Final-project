import axiosInstance from "./axiosInstance";
import { ApiResponse } from "./authApi";

export type NotificationType =
    | "MATCH_APPLIED"
    | "MATCH_CANCELLED"
    | "MATCH_CONFIRMED"
    | "CHAT_RECEIVED"
    | "CHAT_MEMBER_LEFT"
    | "PLACE_VERIFIED"
    | "MEET_REMINDER"
    | "MEET_IMMINENT"
    | "MEET_OVERDUE"
    | "MEET_COMPLETED"
    | "NO_SHOW_CONFIRMED"
    | "NO_SHOW_WARNING"
    | "DISPUTE_SUBMITTED"
    | "DISPUTE_RESULT"
    | "DISPUTE_PENDING"
    | "DISPUTE_DEADLINE_REMINDER"
    | "REPORT_SUBMITTED"
    | "REPORT_REWARD"
    | "REPORT_REJECTED"
    | "REVIEW_DEADLINE_REMINDER"
    | "REVIEW_REWARD"
    | "MANNER_TEMPERATURE_CHANGED"
    | "PAYMENT_SUCCESS"
    | "PAYMENT_FAILED"
    | "PAYMENT_CANCEL_SUCCESS"
    | "PAYMENT_CANCEL_FAILED"
    | "INQUIRY_SUBMITTED"
    | "INQUIRY_ANSWERED"
    | "MEET_EXTEND_REQUESTED"
    | "MEET_EXTEND_ACCEPTED"
    | "MEET_EXTEND_REJECTED"
    | "MEET_EXTEND_EXPIRED"
    | "ACCOUNT_SUSPENDED"
    | "ACCOUNT_UNSUSPENDED"
    | "POST_WARNED_1"
    | "POST_WARNED_2"
    | "POST_EXPIRING_SOON"
    | "POST_EXPIRED"
    | "POST_DELETED"
    | "POST_RESTORED"
    | "SYSTEM";

export type NotificationDomain =
    | "MATCH"
    | "MEET"
    | "CHAT"
    | "POINT"
    | "REPORT"
    | "DISPUTE"
    | "INQUIRY"
    | "ACCOUNT"
    | "POST"
    | "SYSTEM";

export interface NotificationResponse {
    notificationId: number;
    type: NotificationType;
    title: string;
    content: string;
    domain: NotificationDomain;
    relatedId: number | null;
    isRead: boolean;
    readAt: string | null;
    createdAt: string;
}

export interface UpdateNotificationReadResponse {
    notificationId: number;
    isRead: boolean;
    readAt: string;
}

export interface CursorResponse<T> {
    content: T[];
    hasNext: boolean;
    nextCursor: number | null;
}

export const getNotifications = (cursorId: number | null = null, size: number = 10) =>
    axiosInstance.get<ApiResponse<CursorResponse<NotificationResponse>>>("/api/v1/notifications", {
        params: {
            ...(cursorId !== null ? { cursorId } : {}),
            size,
        },
    });

export const getUnreadNotificationCount = () =>
    axiosInstance.get<ApiResponse<{ unreadCount: number }>>("/api/v1/notifications/unread-count");

export const markAllNotificationsRead = () =>
    axiosInstance.patch<ApiResponse<{ updatedCount: number }>>("/api/v1/notifications/read-all");

export const markNotificationRead = (notificationId: number) =>
    axiosInstance.patch<ApiResponse<UpdateNotificationReadResponse>>(
        `/api/v1/notifications/${notificationId}/read`,
    );
