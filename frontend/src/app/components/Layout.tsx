import { useEffect, useState } from 'react';
import { Outlet, Link, useLocation, useNavigate } from 'react-router';
import { User, Bell, Sparkles } from 'lucide-react';
import { toast } from 'sonner';
import { logout } from '@/api/authApi';
import { getUserMe } from '@/api/userApi';
import { clearAccessToken, getAccessToken } from '@/api/axiosInstance';
import { isSuspendedAllowedPath, setUserStatus, useAuthStatus } from '@/store/authStatusStore';
import { NotificationResponse } from '@/api/notificationApi';
import { useNotifications } from '@/store/notificationStore';
import MobileLoggedInNavigation from './MobileLoggedInNavigation';
import { formatNotificationText, getNotificationContextLabel, getNotificationTargetPath } from '../notificationNavigation';

export default function Layout() {
  const location = useLocation();
  const navigate = useNavigate();
  const [point, setPoint] = useState<number | null>(null);
  const { isSuspended } = useAuthStatus();
  const {
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
  } = useNotifications();
  const suspendedToastMessage = '정지된 계정입니다. 문의하기로 이의를 제기해 주세요.';

  const isActive = (path: string) => {
    return location.pathname.startsWith(path);
  };

  useEffect(() => {
    const fetchMyPoint = async () => {
      // sessionStorage → 메모리에서 토큰 확인
      if (!getAccessToken()) {
        setPoint(null);
        clearNotifications();
        return;
      }

      try {
        const userRes = await getUserMe();
        const user = userRes.data.data;
        setPoint(user.point);
        setUserStatus(user.status);

      } catch (err) {
        console.error('Failed to load user header data', err);
        setPoint(null);
        clearNotifications();
        return;
      }
    };

    fetchMyPoint();
    window.addEventListener('focus', fetchMyPoint);

    return () => {
      window.removeEventListener('focus', fetchMyPoint);
    };
  }, [clearNotifications, location.pathname]);

  useEffect(() => {
    if (!isSuspended || isSuspendedAllowedPath(location.pathname)) {
      return;
    }

    toast.warning(suspendedToastMessage);
    navigate('/me', { replace: true });
  }, [isSuspended, location.pathname, navigate]);

  useEffect(() => {
    closeNotifications();
  }, [closeNotifications, location.pathname]);

  useEffect(() => {
    if (!notificationOpen) {
      return;
    }

    const handlePointerDown = (event: MouseEvent | TouchEvent) => {
      const target = event.target;
      if (!(target instanceof Element) || !target.closest('[data-notification-container]')) {
        closeNotifications();
      }
    };
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        closeNotifications();
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
  }, [closeNotifications, notificationOpen]);

  const handleLogout = async () => {
    try {
      await logout();
    } catch (err) {
      console.error('Logout request failed', err);
    } finally {
      clearAccessToken();
      clearNotifications();
      navigate('/');
    }
  };

  const handleNotificationClick = async (notification: NotificationResponse) => {
    await markRead(notification);
    closeNotifications();
    if (isSuspended) {
      return;
    }

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
            onNotificationToggle={toggleNotifications}
            onNotificationClick={handleNotificationClick}
            onMarkAllRead={markAllRead}
            onLoadMore={loadMoreNotifications}
            onLogout={handleLogout}
            isSuspended={isSuspended}
            onSuspendedMenuClick={() => toast.warning(suspendedToastMessage)}
            hideAt="lg"
        />

        <header className="sticky top-0 z-50 hidden border-b border-white/25 bg-white/88 shadow-sm shadow-[#f97316]/5 lg:block">
          <div className="mx-auto grid h-20 max-w-screen-xl grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-5 px-4 lg:px-6">
            <Link to="/" className="hankki-logo shrink-0">
              <span className="hankki-logo-mark" aria-hidden="true">
                <span className="hankki-logo-steam hankki-logo-steam-one" />
                <span className="hankki-logo-steam hankki-logo-steam-two" />
                <span className="hankki-logo-bowl" />
              </span>
              <span className="hankki-logo-text">한끼팟</span>
            </Link>

            <nav className="hankki-nav-pill flex min-w-0 items-center justify-self-center">
              <Link
                  to="/posts"
                  onClick={isSuspendedLinkDisabled('/posts') ? handleSuspendedMenuClick : undefined}
                  aria-disabled={isSuspendedLinkDisabled('/posts')}
                  className={`hankki-nav-link text-sm ${isActive('/posts') ? 'bg-[#fff7ed]/70 text-[#ba4318] shadow-[0_8px_18px_rgba(194,65,24,0.12)]' : ''} ${getSuspendedLinkClass('/posts')}`}
              >
                게시글
              </Link>
              <Link
                  to="/matches"
                  onClick={isSuspendedLinkDisabled('/matches') ? handleSuspendedMenuClick : undefined}
                  aria-disabled={isSuspendedLinkDisabled('/matches')}
                  className={`hankki-nav-link text-sm ${isActive('/matches') ? 'bg-[#fff7ed]/70 text-[#ba4318] shadow-[0_8px_18px_rgba(194,65,24,0.12)]' : ''} ${getSuspendedLinkClass('/matches')}`}
              >
                매칭
              </Link>
              <Link
                  to="/payments"
                  onClick={isSuspendedLinkDisabled('/payments') ? handleSuspendedMenuClick : undefined}
                  aria-disabled={isSuspendedLinkDisabled('/payments')}
                  className={`hankki-nav-link text-sm ${isActive('/payments') ? 'bg-[#fff7ed]/70 text-[#ba4318] shadow-[0_8px_18px_rgba(194,65,24,0.12)]' : ''} ${getSuspendedLinkClass('/payments')}`}
              >
                결제
              </Link>
              <Link
                  to="/me/support"
                  className={`hankki-nav-link text-sm ${isActive('/me/support') || isActive('/me/inquiries') || isActive('/faq') ? 'bg-[#fff7ed]/70 text-[#ba4318] shadow-[0_8px_18px_rgba(194,65,24,0.12)]' : ''}`}
              >
                고객센터
              </Link>
              <Link
                  to="/ai/matching"
                  onClick={isSuspendedLinkDisabled('/ai/matching') ? handleSuspendedMenuClick : undefined}
                  aria-disabled={isSuspendedLinkDisabled('/ai/matching')}
                  className={`hankki-nav-link flex items-center gap-1 text-sm ${isActive('/ai/matching') ? 'bg-[#fff7ed]/70 text-[#ba4318] shadow-[0_8px_18px_rgba(194,65,24,0.12)]' : ''} ${getSuspendedLinkClass('/ai/matching')}`}
              >
                <Sparkles size={15} />
                AI 추천
              </Link>
            </nav>

            <div className="flex shrink-0 items-center gap-3 lg:gap-4">
              <div className="hankki-point-chip flex items-center gap-1 rounded-full px-3 py-1.5">
                <span className="text-[#ef6c00] text-sm">💰</span>
                <span className="text-[#ef6c00] text-sm font-semibold">
                  {point === null ? '-' : `${point.toLocaleString()}P`}
                </span>
              </div>
              <div data-notification-container className="relative">
                <button
                    type="button"
                    onClick={toggleNotifications}
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
                    <div className="hankki-notification-popover absolute right-0 top-9 z-50 w-[min(20rem,calc(100vw-2rem))] overflow-hidden rounded-xl border border-[#e0e0e0] bg-white shadow-xl">
                      <div className="flex items-center justify-between border-b border-[#eeeeee] px-4 py-3">
                        <h3 className="text-sm font-bold text-[#212121]">알림</h3>
                        <button
                            type="button"
                            onClick={markAllRead}
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
                                    <p className="text-sm font-bold text-[#212121]">{formatNotificationText(notification.title)}</p>
                                    {!notification.isRead && (
                                        <span className="shrink-0 rounded-full bg-[#d84315] px-2 py-0.5 text-[10px] font-bold text-white">
                                          NEW
                                        </span>
                                    )}
                                  </div>
                                  <p className="line-clamp-2 text-xs text-[#616161]">{formatNotificationText(notification.content)}</p>
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
                                    onClick={loadMoreNotifications}
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
                  className="hankki-profile-button flex h-8 w-8 items-center justify-center rounded-full"
              >
                <User size={18} className="text-[#616161]" />
              </Link>
              <button
                  type="button"
                  onClick={handleLogout}
                  className="hankki-login-link px-3 py-2 text-xs font-semibold transition-colors"
              >
                로그아웃
              </button>
            </div>
          </div>
        </header>

        <main className="mx-auto w-full max-w-screen-lg px-4 py-6">
          {isSuspended && (
              <div className="mb-5 flex flex-col gap-3 rounded-lg border border-[#ffcc80] bg-[#fff8e1] px-4 py-3 text-sm text-[#5d4037] sm:flex-row sm:items-center sm:justify-between">
                <span>계정이 정지된 상태입니다. 문의하기를 통해 이의를 제기할 수 있습니다.</span>
                <button
                    type="button"
                    onClick={() => navigate('/me/support/inquiries')}
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
