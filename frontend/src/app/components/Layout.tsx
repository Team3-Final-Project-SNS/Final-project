import { useEffect, useState } from 'react';
import { Outlet, Link, useLocation } from 'react-router';
import { User, Bell, Sparkles } from 'lucide-react';
import { getUserMe } from '../../api/userApi';
import {
  getNotifications,
  getUnreadNotificationCount,
  markAllNotificationsRead,
  NotificationResponse,
} from '../../api/notificationApi';

export default function Layout() {
  const location = useLocation();
  const [point, setPoint] = useState<number | null>(null);
  const [notifications, setNotifications] = useState<NotificationResponse[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [notificationOpen, setNotificationOpen] = useState(false);
  const [notificationLoading, setNotificationLoading] = useState(false);

  const isActive = (path: string) => {
    return location.pathname.startsWith(path);
  };

  useEffect(() => {
    const fetchMyPoint = async () => {
      if (!sessionStorage.getItem('accessToken')) {
        setPoint(null);
        setUnreadCount(0);
        return;
      }

      try {
        const [userRes, unreadRes] = await Promise.all([
          getUserMe(),
          getUnreadNotificationCount(),
        ]);
        setPoint(userRes.data.data.point);
        setUnreadCount(unreadRes.data.data.unreadCount);
      } catch (err) {
        console.error('Failed to load header data', err);
        setPoint(null);
      }
    };

    fetchMyPoint();
    window.addEventListener('focus', fetchMyPoint);

    return () => {
      window.removeEventListener('focus', fetchMyPoint);
    };
  }, [location.pathname]);

  const handleNotificationToggle = async () => {
    const nextOpen = !notificationOpen;
    setNotificationOpen(nextOpen);

    if (!nextOpen || !sessionStorage.getItem('accessToken')) {
      return;
    }

    setNotificationLoading(true);
    try {
      const res = await getNotifications(0, 10);
      setNotifications(res.data.data.content);
    } catch (err) {
      console.error('Failed to load notifications', err);
      setNotifications([]);
    } finally {
      setNotificationLoading(false);
    }
  };

  const handleReadAllNotifications = async () => {
    try {
      await markAllNotificationsRead();
      setNotifications((prev) => prev.map((item) => ({ ...item, isRead: true })));
      setUnreadCount(0);
    } catch (err) {
      console.error('Failed to mark notifications read', err);
    }
  };

  return (
      <div className="min-h-screen bg-gradient-to-br from-[#fff7ed] via-[#f7fbff] to-[#eaf7f1]">
        <header className="bg-white border-b border-[#e0e0e0] sticky top-0 z-50">
          <div className="max-w-screen-lg mx-auto px-4 h-14 flex items-center justify-between">
            <Link to="/" className="text-2xl font-bold text-[#d84315]">
              한끼팟
            </Link>

            <nav className="flex items-center gap-8">
              <Link
                  to="/posts"
                  className={`text-sm ${isActive('/posts') ? 'text-[#d84315] font-semibold' : 'text-[#424242]'}`}
              >
                게시글
              </Link>
              <Link
                  to="/matches"
                  className={`text-sm ${isActive('/matches') ? 'text-[#d84315] font-semibold' : 'text-[#424242]'}`}
              >
                매칭
              </Link>
              <Link
                  to="/ai/matching"
                  className={`flex items-center gap-1 text-sm ${isActive('/ai/matching') ? 'text-[#d84315] font-semibold' : 'text-[#424242]'}`}
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
              <div className="relative">
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
                            onClick={handleReadAllNotifications}
                            className="text-xs font-semibold text-[#d84315] hover:underline"
                        >
                          모두 읽음
                        </button>
                      </div>

                      {notificationLoading ? (
                          <div className="px-4 py-8 text-center text-sm text-[#9e9e9e]">알림을 불러오는 중...</div>
                      ) : notifications.length > 0 ? (
                          <div className="max-h-96 overflow-y-auto">
                            {notifications.map((notification) => (
                                <div
                                    key={notification.notificationId}
                                    className={`border-b border-[#f5f5f5] px-4 py-3 ${
                                        notification.isRead ? 'bg-white' : 'bg-[#fff3e0]'
                                    }`}
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
                                  <p className="mt-2 text-[11px] font-semibold text-[#9e9e9e]">
                                    {new Date(notification.createdAt).toLocaleString('ko-KR', {
                                      month: 'numeric',
                                      day: 'numeric',
                                      hour: '2-digit',
                                      minute: '2-digit',
                                    })}
                                  </p>
                                </div>
                            ))}
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
            </div>
          </div>
        </header>

        <main className="max-w-screen-lg mx-auto px-4 py-6">
          <Outlet />
        </main>
      </div>
  );
}
