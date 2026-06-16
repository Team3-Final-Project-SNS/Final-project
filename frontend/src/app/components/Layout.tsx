import { useEffect, useRef, useState } from 'react';
import { Outlet, Link, useLocation, useNavigate } from 'react-router';
import { User, Bell, Sparkles } from 'lucide-react';
import { toast } from 'sonner';
import { logout } from '@/api/authApi';
import { getUserMe } from '@/api/userApi';
import { clearAccessToken, getAccessToken } from '@/api/axiosInstance';
import { isSuspendedAllowedPath, setUserStatus, useAuthStatus } from '@/store/authStatusStore';
import {
  getNotifications,
  getUnreadNotificationCount,
  markAllNotificationsRead,
  markNotificationRead,
  NotificationResponse,
} from '@/api/notificationApi';
import MobileLoggedInNavigation from './MobileLoggedInNavigation';
import { getNotificationContextLabel, getNotificationTargetPath } from '../notificationNavigation';

export default function Layout() {
  const location = useLocation();
  const navigate = useNavigate();
  const [point, setPoint] = useState<number | null>(null);
  const [notifications, setNotifications] = useState<NotificationResponse[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [notificationOpen, setNotificationOpen] = useState(false);
  const [notificationLoading, setNotificationLoading] = useState(false);
  const [notificationLoadingMore, setNotificationLoadingMore] = useState(false);
  const [notificationHasNext, setNotificationHasNext] = useState(false);
  const [notificationNextCursor, setNotificationNextCursor] = useState<number | null>(null);
  const notificationRef = useRef<HTMLDivElement>(null);
  const notificationReadInFlightRef = useRef(new Set<number>());
  const { isSuspended } = useAuthStatus();
  const suspendedToastMessage = '정지된 계정입니다. 문의하기로 이의를 제기해 주세요.';

  const isActive = (path: string) => {
    return location.pathname.startsWith(path);
  };

  useEffect(() => {
    const fetchMyPoint = async () => {
      // sessionStorage → 메모리에서 토큰 확인
      if (!getAccessToken()) {
        setPoint(null);
        setUnreadCount(0);
        return;
      }

      try {
        const userRes = await getUserMe();
        const user = userRes.data.data;
        setPoint(user.point);
        setUserStatus(user.status);

        if (user.status === 'SUSPENDED') {
          setUnreadCount(0);
          return;
        }
      } catch (err) {
        console.error('Failed to load user header data', err);
        setPoint(null);
        setUnreadCount(0);
        return;
      }

      try {
        const unreadRes = await getUnreadNotificationCount();
        setUnreadCount(unreadRes.data.data.unreadCount);
      } catch (err) {
        console.error('Failed to load unread notification count', err);
        setUnreadCount(0);
      }
    };

    fetchMyPoint();
    window.addEventListener('focus', fetchMyPoint);

    return () => {
      window.removeEventListener('focus', fetchMyPoint);
    };
  }, [location.pathname]);

  useEffect(() => {
    if (!isSuspended || isSuspendedAllowedPath(location.pathname)) {
      return;
    }

    toast.warning(suspendedToastMessage);
    navigate('/me', { replace: true });
  }, [isSuspended, location.pathname, navigate]);

  useEffect(() => {
    setNotificationOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    if (!notificationOpen) {
      return;
    }

    const handlePointerDown = (event: MouseEvent | TouchEvent) => {
      const target = event.target;
      if (!(target instanceof Element) || !target.closest('[data-notification-container]')) {
        setNotificationOpen(false);
      }
    };
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setNotificationOpen(false);
      }
    };

    document.addEventListener('mousedown', handlePointerDown);
    document.addEventListener('touchstart', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);

    return () => {
      document.removeEventListener('mousedown', handlePointerDown);
      document.removeEventListener('touchstart', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [notificationOpen]);

  const handleNotificationToggle = async () => {
    if (isSuspended) {
      toast.warning(suspendedToastMessage);
      return;
    }

    const nextOpen = !notificationOpen;
    setNotificationOpen(nextOpen);

    if (!nextOpen || !getAccessToken()) {
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
  };

  const handleLogout = async () => {
    try {
      await logout();
    } catch (err) {
      console.error('Logout request failed', err);
    } finally {
      clearAccessToken();
      navigate('/');
    }
  };

  const handleMarkAllNotificationsRead = async () => {
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
  };

  const handleLoadMoreNotifications = async () => {
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
  };

  const handleNotificationClick = async (notification: NotificationResponse) => {
    if (!notification.isRead && !notificationReadInFlightRef.current.has(notification.notificationId)) {
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
    }

    setNotificationOpen(false);
    const targetPath = getNotificationTargetPath(notification);
    if (targetPath) {
      navigate(targetPath);
    }
  };

  const handleSuspendedMenuClick = (event: React.MouseEvent<HTMLAnchorElement>) => {
    if (!isSuspended) {
      return;
    }

    event.preventDefault();
    toast.warning(suspendedToastMessage);
  };

  const isSuspendedLinkDisabled = (path: string) => isSuspended && !isSuspendedAllowedPath(path);
  const getSuspendedLinkClass = (path: string) =>
    isSuspendedLinkDisabled(path) ? 'cursor-not-allowed opacity-45' : '';

  return (
      <div className="min-h-screen bg-gradient-to-br from-[#fff7ed] via-[#f7fbff] to-[#eaf7f1] pb-[68px] md:pb-0">
        <MobileLoggedInNavigation
            point={point}
            unreadCount={unreadCount}
            notifications={notifications}
            notificationOpen={notificationOpen}
            notificationLoading={notificationLoading}
            notificationLoadingMore={notificationLoadingMore}
            notificationHasNext={notificationHasNext}
            onNotificationToggle={handleNotificationToggle}
            onNotificationClick={handleNotificationClick}
            onMarkAllRead={handleMarkAllNotificationsRead}
            onLoadMore={handleLoadMoreNotifications}
            onLogout={handleLogout}
            isSuspended={isSuspended}
            onSuspendedMenuClick={() => toast.warning(suspendedToastMessage)}
        />

        <header className="sticky top-0 z-50 hidden border-b border-[#e0e0e0] bg-white md:block">
          <div className="max-w-screen-xl mx-auto px-6 h-16 flex items-center justify-between">
            <Link to="/" className="text-2xl font-bold text-[#d84315]">
              한끼팟
            </Link>

            <nav className="flex items-center gap-8">
              <Link
                  to="/posts"
                  onClick={isSuspendedLinkDisabled('/posts') ? handleSuspendedMenuClick : undefined}
                  aria-disabled={isSuspendedLinkDisabled('/posts')}
                  className={`text-sm ${isActive('/posts') ? 'text-[#d84315] font-semibold' : 'text-[#424242]'} ${getSuspendedLinkClass('/posts')}`}
              >
                게시글
              </Link>
              <Link
                  to="/matches"
                  onClick={isSuspendedLinkDisabled('/matches') ? handleSuspendedMenuClick : undefined}
                  aria-disabled={isSuspendedLinkDisabled('/matches')}
                  className={`text-sm ${isActive('/matches') ? 'text-[#d84315] font-semibold' : 'text-[#424242]'} ${getSuspendedLinkClass('/matches')}`}
              >
                매칭
              </Link>
              <Link
                  to="/payments"
                  onClick={isSuspendedLinkDisabled('/payments') ? handleSuspendedMenuClick : undefined}
                  aria-disabled={isSuspendedLinkDisabled('/payments')}
                  className={`text-sm ${isActive('/payments') ? 'text-[#d84315] font-semibold' : 'text-[#424242]'} ${getSuspendedLinkClass('/payments')}`}
              >
                결제
              </Link>
              <Link
                  to="/me/inquiries"
                  className={`text-sm ${isActive('/me/inquiries') ? 'text-[#d84315] font-semibold' : 'text-[#424242]'}`}
              >
                고객센터
              </Link>
              <Link
                  to="/ai/matching"
                  onClick={isSuspendedLinkDisabled('/ai/matching') ? handleSuspendedMenuClick : undefined}
                  aria-disabled={isSuspendedLinkDisabled('/ai/matching')}
                  className={`flex items-center gap-1 text-sm ${isActive('/ai/matching') ? 'text-[#d84315] font-semibold' : 'text-[#424242]'} ${getSuspendedLinkClass('/ai/matching')}`}
              >
                <Sparkles size={15} />
                AI 추천
              </Link>
            </nav>

            <div className="flex items-center gap-4">
              <div className="flex items-center gap-1 bg-[#fff3e0] px-3 py-1.5 rounded-full">
                <span className="text-[#ef6c00] text-sm">💰</span>
                <span className="text-[#ef6c00] text-sm font-semibold">
                  {point === null ? '-' : `${point.toLocaleString()}P`}
                </span>
              </div>
              <div ref={notificationRef} data-notification-container className="relative">
                <button
                    type="button"
                    onClick={handleNotificationToggle}
                    className="relative text-[#616161] hover:text-[#d84315]"
                    aria-label="알림 목록"
                >
                  <Bell size={20} />
                  {unreadCount > 0 && (
                      <span className="absolute -right-1.5 -top-1.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-[#d84315] px-1 text-[10px] font-bold text-white">
                        {unreadCount > 9 ? '9+' : unreadCount}
                      </span>
                  )}
                </button>

                {notificationOpen && (
                    <div className="absolute right-0 top-9 z-50 w-80 overflow-hidden rounded-xl border border-[#e0e0e0] bg-white shadow-xl">
                      <div className="flex items-center justify-between border-b border-[#eeeeee] px-4 py-3">
                        <h3 className="text-sm font-bold text-[#212121]">알림</h3>
                        <button
                            type="button"
                            onClick={handleMarkAllNotificationsRead}
                            disabled={unreadCount === 0}
                            className="text-xs font-semibold text-[#d84315] hover:text-[#bf360c] disabled:cursor-not-allowed disabled:text-[#bdbdbd]"
                        >
                          모두 읽음
                        </button>
                      </div>

                      {notificationLoading ? (
                          <div className="px-4 py-8 text-center text-sm text-[#9e9e9e]">알림을 불러오는 중...</div>
                      ) : notifications.length > 0 ? (
                          <div className="max-h-96 overflow-y-auto">
                            {notifications.map((notification) => (
                                <button
                                    type="button"
                                    key={notification.notificationId}
                                    onClick={() => handleNotificationClick(notification)}
                                    className={`w-full border-b border-[#f5f5f5] px-4 py-3 text-left ${
                                        notification.isRead ? 'bg-white' : 'bg-[#fff3e0]'
                                    } hover:bg-[#fff8f2]`}
                                >
                                  <div className="mb-1 flex items-start justify-between gap-2">
                                    <p className="text-sm font-bold text-[#212121]">{notification.title}</p>
                                    {!notification.isRead && (
                                        <span className="shrink-0 rounded-full bg-[#d84315] px-2 py-0.5 text-[10px] font-bold text-white">
                                          NEW
                                        </span>
                                    )}
                                  </div>
                                  <p className="line-clamp-2 text-xs text-[#616161]">{notification.content}</p>
                                  {getNotificationContextLabel(notification) && (
                                      <p className="mt-1 text-[11px] font-bold text-[#d84315]">
                                        {getNotificationContextLabel(notification)}
                                      </p>
                                  )}
                                  <p className="mt-2 text-[11px] font-semibold text-[#9e9e9e]">
                                    {new Date(notification.createdAt).toLocaleString('ko-KR', {
                                      month: 'numeric',
                                      day: 'numeric',
                                      hour: '2-digit',
                                      minute: '2-digit',
                                    })}
                                  </p>
                                </button>
                            ))}
                            {notificationHasNext && (
                                <button
                                    type="button"
                                    onClick={handleLoadMoreNotifications}
                                    disabled={notificationLoadingMore}
                                    className="w-full border-t border-[#eeeeee] px-4 py-3 text-xs font-semibold text-[#d84315] hover:bg-[#fff8f2] disabled:cursor-not-allowed disabled:text-[#bdbdbd]"
                                >
                                  {notificationLoadingMore ? '불러오는 중...' : '이전 알림 더 보기'}
                                </button>
                            )}
                          </div>
                      ) : (
                          <div className="px-4 py-8 text-center text-sm text-[#9e9e9e]">알림이 없습니다.</div>
                      )}
                    </div>
                )}
              </div>
              <Link
                  to="/me"
                  title="내 정보 보기"
                  aria-label="내 정보 보기"
                  className="w-8 h-8 bg-[#e0e0e0] rounded-full flex items-center justify-center"
              >
                <User size={18} className="text-[#616161]" />
              </Link>
              <button
                  type="button"
                  onClick={handleLogout}
                  className="text-xs text-[#9e9e9e] hover:text-[#d84315]"
              >
                로그아웃
              </button>
            </div>
          </div>
        </header>

        <main className="max-w-screen-lg mx-auto px-4 py-6">
          {isSuspended && (
              <div className="mb-5 flex flex-col gap-3 rounded-lg border border-[#ffcc80] bg-[#fff8e1] px-4 py-3 text-sm text-[#5d4037] sm:flex-row sm:items-center sm:justify-between">
                <span>계정이 정지된 상태입니다. 문의하기를 통해 이의를 제기할 수 있습니다.</span>
                <button
                    type="button"
                    onClick={() => navigate('/me/inquiries')}
                    className="inline-flex shrink-0 items-center justify-center rounded-md bg-[#d84315] px-3 py-2 text-xs font-bold text-white hover:bg-[#bf360c]"
                >
                  문의하기
                </button>
              </div>
          )}
          <Outlet />
        </main>
      </div>
  );
}
