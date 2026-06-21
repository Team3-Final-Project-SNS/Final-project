import { useEffect, useRef, useState } from 'react';
import { Bell, CheckCheck, Loader2 } from 'lucide-react';
import { useNavigate } from 'react-router';
import {
  getAdminNotifications,
  getAdminUnreadNotificationCount,
  markAdminNotificationRead,
  markAllAdminNotificationsRead,
  subscribeAdminNotifications,
} from '../../api/adminNotificationApi';
import { NotificationResponse } from '../../api/notificationApi';
import {
  formatNotificationText,
  getNotificationContextLabel,
  getNotificationTargetPath,
} from '../notificationNavigation';

export default function AdminNotificationBell() {
  const navigate = useNavigate();
  const containerRef = useRef<HTMLDivElement>(null);
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState<NotificationResponse[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [hasNext, setHasNext] = useState(false);
  const [nextCursor, setNextCursor] = useState<number | null>(null);

  useEffect(() => {
    const refreshUnreadCount = () => {
      getAdminUnreadNotificationCount()
        .then((response) => setUnreadCount(response.data.data.unreadCount))
        .catch(() => setUnreadCount(0));
    };

    refreshUnreadCount();
    const intervalId = window.setInterval(refreshUnreadCount, 30_000);

    return () => window.clearInterval(intervalId);
  }, []);

  useEffect(() => {
    const eventSource = subscribeAdminNotifications();
    if (!eventSource) return;

    const handleNotification = (event: MessageEvent) => {
      try {
        const notification = JSON.parse(event.data) as NotificationResponse;
        setUnreadCount((current) => current + (notification.isRead ? 0 : 1));
        setItems((current) => {
          if (current.some((item) => item.notificationId === notification.notificationId)) {
            return current;
          }
          return [notification, ...current].slice(0, 10);
        });
      } catch (err) {
        console.error('Failed to parse admin SSE notification', err);
      }
    };

    eventSource.addEventListener('notification', handleNotification);
    eventSource.onerror = (event) => {
      console.error('Admin notification SSE connection failed', event);
    };

    return () => {
      eventSource.removeEventListener('notification', handleNotification);
      eventSource.close();
    };
  }, []);

  useEffect(() => {
    if (!open) return;

    const closeOnOutside = (event: MouseEvent) => {
      const target = event.target;
      if (target instanceof Node && !containerRef.current?.contains(target)) {
        setOpen(false);
      }
    };
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false);
    };

    document.addEventListener('mousedown', closeOnOutside);
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      document.removeEventListener('mousedown', closeOnOutside);
      document.removeEventListener('keydown', closeOnEscape);
    };
  }, [open]);

  const toggle = async () => {
    const nextOpen = !open;
    setOpen(nextOpen);
    if (!nextOpen) return;

    setLoading(true);
    try {
      const response = await getAdminNotifications(null, 10);
      const page = response.data.data;
      setItems(page.content);
      setHasNext(page.hasNext);
      setNextCursor(page.nextCursor);
    } finally {
      setLoading(false);
    }
  };

  const readAll = async () => {
    await markAllAdminNotificationsRead();
    setUnreadCount(0);
    setItems((current) => current.map((item) => ({
      ...item,
      isRead: true,
      readAt: item.readAt ?? new Date().toISOString(),
    })));
  };

  const loadMore = async () => {
    if (loadingMore || !hasNext || nextCursor === null) return;

    setLoadingMore(true);
    try {
      const response = await getAdminNotifications(nextCursor, 10);
      const page = response.data.data;
      setItems((current) => [...current, ...page.content]);
      setHasNext(page.hasNext);
      setNextCursor(page.nextCursor);
    } finally {
      setLoadingMore(false);
    }
  };

  const handleClick = async (notification: NotificationResponse) => {
    if (!notification.isRead) {
      await markAdminNotificationRead(notification.notificationId);
      setUnreadCount((current) => Math.max(0, current - 1));
    }

    setOpen(false);
    const targetPath = getNotificationTargetPath(notification);
    if (targetPath) navigate(targetPath);
  };

  return (
    <div ref={containerRef} className="relative z-[60]">
      <button
        type="button"
        onClick={toggle}
        aria-label="관리자 알림"
        className="relative flex h-6 w-6 items-center justify-center text-[#616161] hover:text-[#d84315]"
      >
        <Bell size={20} />
        {unreadCount > 0 && (
          <span className="absolute -right-1.5 -top-1.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-[#d84315] px-1 text-[10px] font-bold text-white">
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 top-9 w-[min(20rem,calc(100vw-2rem))] overflow-hidden rounded-xl border border-[#e0e0e0] bg-white shadow-xl">
          <div className="flex items-center justify-between border-b border-[#eeeeee] px-4 py-3">
            <h3 className="text-sm font-bold text-[#212121]">관리자 알림</h3>
            <button
              type="button"
              onClick={readAll}
              disabled={unreadCount === 0}
              className="inline-flex items-center gap-1 text-xs font-semibold text-[#d84315] disabled:cursor-not-allowed disabled:text-[#bdbdbd]"
            >
              <CheckCheck size={14} />
              모두 읽음
            </button>
          </div>

          <div className="max-h-96 overflow-y-auto">
            {loading ? (
              <div className="flex items-center justify-center gap-2 p-10 text-sm text-[#9e9e9e]">
                <Loader2 className="animate-spin" size={18} />
                알림을 불러오는 중...
              </div>
            ) : items.length ? (
              <>
                {items.map((item) => (
                  <button
                    key={item.notificationId}
                    type="button"
                    onClick={() => handleClick(item)}
                    className={`block w-full border-b border-[#f5f5f5] px-4 py-3 text-left hover:bg-[#fff8f2] ${
                      item.isRead ? 'bg-white' : 'bg-[#fff3e0]'
                    }`}
                  >
                    <div className="mb-1 flex items-start justify-between gap-2">
                      <p className="text-sm font-bold text-[#212121]">{formatNotificationText(item.title)}</p>
                      {!item.isRead && (
                        <span className="shrink-0 rounded-full bg-[#d84315] px-2 py-0.5 text-[10px] font-bold text-white">
                          NEW
                        </span>
                      )}
                    </div>
                    <p className="line-clamp-2 text-xs text-[#616161]">{formatNotificationText(item.content)}</p>
                    {getNotificationContextLabel(item) && (
                      <p className="mt-1 text-[11px] font-bold text-[#d84315]">
                        {getNotificationContextLabel(item)}
                      </p>
                    )}
                    <p className="mt-2 text-[11px] font-semibold text-[#9e9e9e]">{formatDateTime(item.createdAt)}</p>
                  </button>
                ))}
                {hasNext && (
                  <button
                    type="button"
                    disabled={loadingMore}
                    onClick={loadMore}
                    className="w-full py-3 text-xs font-bold text-[#d84315] disabled:opacity-60"
                  >
                    {loadingMore ? '불러오는 중...' : '이전 알림 더 보기'}
                  </button>
                )}
              </>
            ) : (
              <div className="p-10 text-center text-sm text-[#9e9e9e]">도착한 관리자 알림이 없습니다.</div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function formatDateTime(value: string) {
  return new Date(value).toLocaleString('ko-KR', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}
