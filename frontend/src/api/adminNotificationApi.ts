import axiosInstance from './axiosInstance';
import { ApiResponse } from './authApi';
import {
  CursorResponse,
  NotificationResponse,
  UpdateNotificationReadResponse,
} from './notificationApi';

export const getAdminNotifications = (cursorId: number | null = null, size: number = 10) =>
  axiosInstance.get<ApiResponse<CursorResponse<NotificationResponse>>>('/api/v1/admin/notifications', {
    params: {
      ...(cursorId !== null ? { cursorId } : {}),
      size,
    },
  });

export const getAdminUnreadNotificationCount = () =>
  axiosInstance.get<ApiResponse<{ unreadCount: number }>>('/api/v1/admin/notifications/unread-count');

export const markAllAdminNotificationsRead = () =>
  axiosInstance.patch<ApiResponse<{ updatedCount: number }>>('/api/v1/admin/notifications/read-all');

export const markAdminNotificationRead = (notificationId: number) =>
  axiosInstance.patch<ApiResponse<UpdateNotificationReadResponse>>(
    `/api/v1/admin/notifications/${notificationId}/read`,
  );

export const subscribeAdminNotifications = () => {
  const token = sessionStorage.getItem('adminAccessToken');
  if (!token) return null;

  const baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
  const url = new URL('/api/v1/admin/notifications/subscribe', baseUrl);
  url.searchParams.set('token', `Bearer ${token}`);
  return new EventSource(url.toString(), { withCredentials: true });
};
