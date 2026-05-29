import axiosInstance from "./axiosInstance";
import { ApiResponse } from "./authApi";
import { PageResponse } from "./postApi";

export type NotificationType =
    | "MATCH_APPLIED"
    | "MATCH_CANCELLED"
    | "MATCH_CONFIRMED"
    | "CHAT_RECEIVED"
    | "PLACE_VERIFIED"
    | "MEET_REMINDER"
    | "MEET_IMMINENT"
    | "NO_SHOW_CONFIRMED"
    | "NO_SHOW_WARNING"
    | "REPORT_RESULT"
    | "DISPUTE_SUBMITTED"
    | "DISPUTE_RESULT"
    | "POINT_CHANGED"
    | "INQUIRY_ANSWERED"
    | "MEET_EXTEND_REQUESTED"
    | "MEET_EXTEND_ACCEPTED"
    | "MEET_EXTEND_REJECTED"
    | "MEET_EXTEND_EXPIRED"
    | "SYSTEM";

export interface NotificationResponse {
    notificationId: number;
    type: NotificationType;
    title: string;
    content: string;
    domain: string;
    relatedId: number | null;
    isRead: boolean;
    readAt: string | null;
    createdAt: string;
}

export const getNotifications = (page: number = 0, size: number = 10) =>
    axiosInstance.get<ApiResponse<PageResponse<NotificationResponse>>>("/api/v1/notifications", {
        params: { page, size },
    });

export const getUnreadNotificationCount = () =>
    axiosInstance.get<ApiResponse<{ unreadCount: number }>>("/api/v1/notifications/unread-count");

export const markAllNotificationsRead = () =>
    axiosInstance.patch<ApiResponse<{ updatedCount: number }>>("/api/v1/notifications/read-all");
