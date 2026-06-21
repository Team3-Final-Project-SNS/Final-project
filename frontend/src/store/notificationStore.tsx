import { createContext, ReactNode, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import {
  getNotifications,
  getUnreadNotificationCount,
  markAllNotificationsRead,
  markNotificationRead,
  NotificationResponse,
  subscribeNotifications,
} from '@/api/notificationApi';
import { ACCESS_TOKEN_CHANGED_EVENT, getAccessToken } from '@/api/axiosInstance';

interface NotificationContextValue {
  notifications: NotificationResponse[];
  unreadCount: number;
  notificationOpen: boolean;
  notificationLoading: boolean;
  notificationLoadingMore: boolean;
  notificationHasNext: boolean;
  toggleNotifications: () => Promise<void>;
  closeNotifications: () => void;
  loadMoreNotifications: () => Promise<void>;
  markAllRead: () => Promise<void>;
  markRead: (notification: NotificationResponse) => Promise<void>;
  clearNotifications: () => void;
}

const NotificationContext = createContext<NotificationContextValue | null>(null);

export function NotificationProvider({ children }: { children: ReactNode }) {
  const [accessToken, setAccessTokenState] = useState(() => getAccessToken());
  const [notifications, setNotifications] = useState<NotificationResponse[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [notificationOpen, setNotificationOpen] = useState(false);
  const [notificationLoading, setNotificationLoading] = useState(false);
  const [notificationLoadingMore, setNotificationLoadingMore] = useState(false);
  const [notificationHasNext, setNotificationHasNext] = useState(false);
  const [notificationNextCursor, setNotificationNextCursor] = useState<number | null>(null);
  const notificationReadInFlightRef = useRef(new Set<number>());

  useEffect(() => {
    const syncAccessToken = () => {
      setAccessTokenState((current) => {
        const latest = getAccessToken();
        return latest === current ? current : latest;
      });
    };

    syncAccessToken();
    window.addEventListener(ACCESS_TOKEN_CHANGED_EVENT, syncAccessToken);
    window.addEventListener('focus', syncAccessToken);

    return () => {
      window.removeEventListener(ACCESS_TOKEN_CHANGED_EVENT, syncAccessToken);
      window.removeEventListener('focus', syncAccessToken);
    };
  }, []);

  const clearNotifications = useCallback(() => {
    setNotifications([]);
    setUnreadCount(0);
    setNotificationOpen(false);
    setNotificationLoading(false);
    setNotificationLoadingMore(false);
    setNotificationHasNext(false);
    setNotificationNextCursor(null);
    notificationReadInFlightRef.current.clear();
  }, []);

  useEffect(() => {
    if (!accessToken) {
      clearNotifications();
      return;
    }

    getUnreadNotificationCount()
      .then((res) => setUnreadCount(res.data.data.unreadCount))
      .catch((err) => {
        console.error('Failed to load unread notification count', err);
        setUnreadCount(0);
      });

    const eventSource = subscribeNotifications();
    if (!eventSource) {
      return;
    }

    const handleNotification = (event: MessageEvent) => {
      try {
        const notification = JSON.parse(event.data) as NotificationResponse;
        setUnreadCount((current) => current + (notification.isRead ? 0 : 1));
        setNotifications((current) => {
          if (current.some((item) => item.notificationId === notification.notificationId)) {
            return current;
          }
          return [notification, ...current].slice(0, 10);
        });
      } catch (err) {
        console.error('Failed to parse SSE notification', err);
      }
    };

    eventSource.addEventListener('notification', handleNotification);
    eventSource.onerror = (event) => {
      console.error('Notification SSE connection failed', event);
    };

    return () => {
      eventSource.removeEventListener('notification', handleNotification);
      eventSource.close();
    };
  }, [accessToken, clearNotifications]);

  const loadFirstPage = useCallback(async () => {
    if (!getAccessToken()) {
      return;
    }

    setNotificationLoading(true);
    try {
      const res = await getNotifications(null, 10);
      const notificationPage = res.data.data;
      setNotifications(notificationPage.content);
      setNotificationHasNext(notificationPage.hasNext);
      setNotificationNextCursor(notificationPage.nextCursor);
    } catch (err) {
      console.error('Failed to load notifications', err);
    } finally {
      setNotificationLoading(false);
    }
  }, []);

  const toggleNotifications = useCallback(async () => {
    const nextOpen = !notificationOpen;
    setNotificationOpen(nextOpen);

    if (nextOpen) {
      await loadFirstPage();
    }
  }, [loadFirstPage, notificationOpen]);

  const closeNotifications = useCallback(() => {
    setNotificationOpen(false);
  }, []);

  const loadMoreNotifications = useCallback(async () => {
    if (notificationLoadingMore || !notificationHasNext || notificationNextCursor === null) {
      return;
    }

    setNotificationLoadingMore(true);
    try {
      const res = await getNotifications(notificationNextCursor, 10);
      const notificationPage = res.data.data;
      setNotifications((current) => [...current, ...notificationPage.content]);
      setNotificationHasNext(notificationPage.hasNext);
      setNotificationNextCursor(notificationPage.nextCursor);
    } catch (err) {
      console.error('Failed to load more notifications', err);
    } finally {
      setNotificationLoadingMore(false);
    }
  }, [notificationHasNext, notificationLoadingMore, notificationNextCursor]);

  const markAllRead = useCallback(async () => {
    try {
      await markAllNotificationsRead();
      setUnreadCount(0);
      setNotifications((current) =>
        current.map((notification) => ({
          ...notification,
          isRead: true,
          readAt: notification.readAt ?? new Date().toISOString(),
        })),
      );
    } catch (err) {
      console.error('Failed to mark notifications as read', err);
    }
  }, []);

  const markRead = useCallback(async (notification: NotificationResponse) => {
    if (notification.isRead || notificationReadInFlightRef.current.has(notification.notificationId)) {
      return;
    }

    notificationReadInFlightRef.current.add(notification.notificationId);
    try {
      const res = await markNotificationRead(notification.notificationId);
      const updatedNotification = res.data.data;

      setNotifications((current) =>
        current.map((item) =>
          item.notificationId === notification.notificationId
            ? {
                ...item,
                isRead: updatedNotification.isRead,
                readAt: updatedNotification.readAt,
              }
            : item,
        ),
      );
      setUnreadCount((current) => Math.max(0, current - 1));
    } catch (err) {
      console.error('Failed to mark notification as read', err);
    } finally {
      notificationReadInFlightRef.current.delete(notification.notificationId);
    }
  }, []);

  const value = useMemo(
    () => ({
      notifications,
      unreadCount,
      notificationOpen,
      notificationLoading,
      notificationLoadingMore,
      notificationHasNext,
      toggleNotifications,
      closeNotifications,
      loadMoreNotifications,
      markAllRead,
      markRead,
      clearNotifications,
    }),
    [
      clearNotifications,
      closeNotifications,
      loadMoreNotifications,
      markAllRead,
      markRead,
      notificationHasNext,
      notificationLoading,
      notificationLoadingMore,
      notificationOpen,
      notifications,
      toggleNotifications,
      unreadCount,
    ],
  );

  return (
    <NotificationContext.Provider value={value}>
      {children}
    </NotificationContext.Provider>
  );
}

export function useNotifications() {
  const context = useContext(NotificationContext);
  if (!context) {
    throw new Error('useNotifications must be used within NotificationProvider');
  }
  return context;
}
