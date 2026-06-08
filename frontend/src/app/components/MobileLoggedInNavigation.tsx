import { useEffect, useState } from 'react';
import { Bell, FileText, Handshake, Home, Menu, Sparkles, User } from 'lucide-react';
import { Link, useLocation } from 'react-router';
import { NotificationResponse } from '../../api/notificationApi';

interface MobileLoggedInNavigationProps {
  point: number | null;
  unreadCount: number;
  notifications: NotificationResponse[];
  notificationOpen: boolean;
  notificationLoading: boolean;
  notificationLoadingMore: boolean;
  notificationHasNext: boolean;
  onNotificationToggle: () => void;
  onNotificationClick: (notification: NotificationResponse) => void;
  onMarkAllRead: () => void;
  onLoadMore: () => void;
  onLogout: () => void;
}

const menuItems = [
  { label: '게시글', to: '/posts' },
  { label: '매칭', to: '/matches' },
  { label: '결제', to: '/payments' },
  { label: '고객센터', to: '/me/inquiries' },
  { label: 'AI 추천', to: '/ai/matching' },
  { label: '마이페이지', to: '/me' },
];

const bottomItems = [
  { label: '홈', to: '/', icon: Home },
  { label: '게시글', to: '/posts', icon: FileText },
  { label: '매칭', to: '/matches', icon: Handshake },
  { label: 'AI 추천', to: '/ai/matching', icon: Sparkles },
  { label: 'MY', to: '/me', icon: User },
];

export default function MobileLoggedInNavigation({
  point,
  unreadCount,
  notifications,
  notificationOpen,
  notificationLoading,
  notificationLoadingMore,
  notificationHasNext,
  onNotificationToggle,
  onNotificationClick,
  onMarkAllRead,
  onLoadMore,
  onLogout,
}: MobileLoggedInNavigationProps) {
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => {
    setMenuOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    if (!menuOpen) {
      return;
    }

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setMenuOpen(false);
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [menuOpen]);

  const isActive = (path: string) => {
    if (path === '/') {
      return location.pathname === '/';
    }
    return location.pathname.startsWith(path);
  };

  return (
      <>
        <header className="sticky top-0 z-50 flex h-16 items-center justify-between border-b border-[#e0e0e0] bg-white px-4 md:hidden">
          <Link to="/" className="text-xl font-bold text-[#d84315]">
            한끼팟
          </Link>

          <div className="flex items-center gap-3">
            <div className="flex items-center gap-1 rounded-full bg-[#fff3e0] px-2.5 py-1.5">
              <span className="text-xs text-[#ef6c00]">💰</span>
              <span className="whitespace-nowrap text-xs font-semibold text-[#ef6c00]">
                {point === null ? '-' : `${point.toLocaleString()}P`}
              </span>
            </div>

            <div data-notification-container className="relative">
              <button
                  type="button"
                  onClick={() => {
                    setMenuOpen(false);
                    onNotificationToggle();
                  }}
                  className="relative flex h-8 w-8 items-center justify-center text-[#616161]"
                  aria-label="알림 목록"
              >
                <Bell size={20} />
                {unreadCount > 0 && (
                    <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-[#d84315] px-1 text-[9px] font-bold text-white">
                      {unreadCount > 9 ? '9+' : unreadCount}
                    </span>
                )}
              </button>

              {notificationOpen && (
                  <div className="absolute right-0 top-10 z-[70] w-[min(20rem,calc(100vw-2rem))] overflow-hidden rounded-xl border border-[#e0e0e0] bg-white shadow-xl">
                    <div className="flex items-center justify-between border-b border-[#eeeeee] px-4 py-3">
                      <h3 className="text-sm font-bold text-[#212121]">알림</h3>
                      <button
                          type="button"
                          onClick={onMarkAllRead}
                          disabled={unreadCount === 0}
                          className="text-xs font-semibold text-[#d84315] disabled:cursor-not-allowed disabled:text-[#bdbdbd]"
                      >
                        모두 읽음
                      </button>
                    </div>

                    {notificationLoading ? (
                        <div className="px-4 py-8 text-center text-sm text-[#9e9e9e]">
                          알림을 불러오는 중...
                        </div>
                    ) : notifications.length > 0 ? (
                        <div className="max-h-[60vh] overflow-y-auto overscroll-contain">
                          {notifications.map((notification) => (
                              <button
                                  type="button"
                                  key={notification.notificationId}
                                  onClick={() => onNotificationClick(notification)}
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
                                  onClick={onLoadMore}
                                  disabled={notificationLoadingMore}
                                  className="w-full border-t border-[#eeeeee] px-4 py-3 text-xs font-semibold text-[#d84315] disabled:cursor-not-allowed disabled:text-[#bdbdbd]"
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

            <button
                type="button"
                onClick={() => {
                  if (notificationOpen) {
                    onNotificationToggle();
                  }
                  setMenuOpen((current) => !current);
                }}
                className="flex h-8 w-8 items-center justify-center text-[#616161]"
                aria-label="전체 메뉴"
                aria-expanded={menuOpen}
            >
              <Menu size={22} />
            </button>
          </div>
        </header>

        {menuOpen && (
            <>
              <button
                  type="button"
                  aria-label="전체 메뉴 닫기"
                  onClick={() => setMenuOpen(false)}
                  className="fixed inset-0 z-40 bg-transparent md:hidden"
              />
              <nav className="fixed right-0 top-16 z-[60] w-[52%] max-w-[220px] bg-white px-4 pb-3 shadow-[-10px_12px_24px_rgba(0,0,0,0.14)] md:hidden">
                <div className="py-2">
                  {menuItems.map((item) => (
                      <Link
                          key={item.to}
                          to={item.to}
                          className="flex h-12 items-center border-b border-[#f1f1f1] px-2 text-sm text-[#333333]"
                      >
                        {item.label}
                      </Link>
                  ))}
                </div>
                <button
                    type="button"
                    onClick={onLogout}
                    className="w-full border-t border-[#eeeeee] pt-2 text-right text-[11px] text-[#9e9e9e]"
                >
                  로그아웃
                </button>
              </nav>
            </>
        )}

        <nav className="fixed bottom-0 left-0 right-0 z-50 grid h-[68px] grid-cols-5 border-t border-[#dedede] bg-white px-1 pb-1 pt-1.5 shadow-[0_-5px_16px_rgba(0,0,0,0.06)] md:hidden">
          {bottomItems.map((item) => {
            const Icon = item.icon;
            const active = isActive(item.to);

            return (
                <Link
                    key={item.to}
                    to={item.to}
                    className={`flex flex-col items-center justify-center gap-1 text-[10px] ${
                        active ? 'font-bold text-[#d84315]' : 'text-[#757575]'
                    }`}
                >
                  <Icon size={20} />
                  {item.label}
                </Link>
            );
          })}
        </nav>
      </>
  );
}
