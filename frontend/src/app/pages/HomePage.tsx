import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { Bell, Sparkles, Utensils, QrCode, Shield, User, Users } from 'lucide-react';
import { logout } from '@/api/authApi';
import { getUserMe } from '@/api/userApi';
import { clearAccessToken, getAccessToken } from '@/api/axiosInstance';
import {
  getNotifications,
  getUnreadNotificationCount,
  markAllNotificationsRead,
  markNotificationRead,
  NotificationResponse,
} from '@/api/notificationApi';
import MobileLoggedInNavigation from '../components/MobileLoggedInNavigation';
import { getNotificationTargetPath } from '../notificationNavigation';

export default function HomePage() {
  const navigate = useNavigate();
  const [isLoggedIn, setIsLoggedIn] = useState(() => Boolean(getAccessToken()));
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

  useEffect(() => {
    const syncLoginState = async () => {
      const loggedIn = Boolean(getAccessToken());
      setIsLoggedIn(loggedIn);

      if (!loggedIn) {
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
        console.error('Failed to load home header data', err);
      }
    };

    syncLoginState();
    window.addEventListener('focus', syncLoginState);

    return () => {
      window.removeEventListener('focus', syncLoginState);
    };
  }, []);

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

  const handleLogout = async () => {
    try {
      await logout();
    } catch (err) {
      console.error('Logout request failed', err);
    } finally {
      clearAccessToken();
      setIsLoggedIn(false);
      setPoint(null);
      setUnreadCount(0);
    }
  };

  const handleNotificationToggle = async () => {
    const nextOpen = !notificationOpen;
    setNotificationOpen(nextOpen);

    if (!nextOpen) {
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

  return (
      <div className={`min-h-screen bg-gradient-to-br from-[#fff7ed] via-[#f7fbff] to-[#eaf7f1] ${
          isLoggedIn ? 'pb-[68px] md:pb-0' : ''
      }`}>
        {isLoggedIn && (
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
            />
        )}

        {/* Header */}
        <header className={`sticky top-0 z-50 border-b border-[#e0e0e0] bg-white/80 backdrop-blur-sm ${
            isLoggedIn ? 'hidden md:block' : ''
        }`}>
          <div className="max-w-screen-xl mx-auto px-6 h-16 flex items-center justify-between">
            {isLoggedIn ? (
                <>
                  <Link to="/" className="text-2xl font-bold text-[#d84315]">
                    한끼팟
                  </Link>
                  <nav className="flex items-center gap-8">
                    <Link to="/posts" className="text-sm text-[#424242] hover:text-[#d84315]">게시글</Link>
                    <Link to="/matches" className="text-sm text-[#424242] hover:text-[#d84315]">매칭</Link>
                    <Link to="/payments" className="text-sm text-[#424242] hover:text-[#d84315]">결제</Link>
                    <Link to="/me/inquiries" className="text-sm text-[#424242] hover:text-[#d84315]">고객센터</Link>
                    <Link to="/ai/matching" className="flex items-center gap-1 text-sm text-[#424242] hover:text-[#d84315]">
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
                                <div className="px-4 py-8 text-center text-sm text-[#9e9e9e]">
                                  알림을 불러오는 중...
                                </div>
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
                                <div className="px-4 py-8 text-center text-sm text-[#9e9e9e]">
                                  알림이 없습니다.
                                </div>
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
                </>
            ) : (
                <>
                  <div className="flex items-center gap-2">
                    <span className="text-3xl">🍚</span>
                    <span className="text-2xl font-bold text-[#d84315]">한끼팟</span>
                  </div>
                  <div className="flex items-center gap-4">
                    <Link
                        to="/login"
                        className="px-4 py-2 text-[#616161] hover:text-[#d84315] font-medium transition-colors"
                    >
                      로그인
                    </Link>
                    <Link
                        to="/signup"
                        className="px-6 py-2.5 bg-[#d84315] text-white rounded-full font-semibold hover:bg-[#bf360c] transition-all shadow-md hover:shadow-lg"
                    >
                      시작하기
                    </Link>
                  </div>
                </>
            )}
          </div>
        </header>

        {/* Hero Section */}
        <section className="max-w-screen-xl mx-auto px-4 pt-12 pb-16 md:px-6 md:pt-20 md:pb-24">
          <div className="grid lg:grid-cols-2 gap-12 items-center">
            <div>
              <div className="inline-block px-4 py-2 bg-[#fff3e0] rounded-full mb-6">
                <span className="text-[#d84315] font-semibold text-sm">🎓 대학생 식사 매칭 플랫폼</span>
              </div>
              <h1 className="mb-5 text-4xl font-bold leading-tight text-[#212121] md:mb-6 md:text-5xl lg:text-6xl">
                식사로 연결되는<br />
                새로운 만남
              </h1>
              <p className="mb-7 text-base leading-relaxed text-[#616161] md:mb-8 md:text-xl">
                학교 친구들과 함께하는 한 끼.<br />
                책임비 시스템으로 안전하게, QR 인증으로 확실하게.
              </p>
              <div className="grid grid-cols-2 gap-3 md:flex md:gap-4">
                <Link
                    to="/posts"
                    className="rounded-xl bg-[#d84315] px-3 py-4 text-center text-sm font-bold text-white shadow-lg transition-all hover:bg-[#bf360c] hover:shadow-xl md:px-8 md:text-lg md:hover:scale-105"
                >
                  게시글 둘러보기
                </Link>
                <Link
                    to={isLoggedIn ? '/me/matches' : '/signup'}
                    className="rounded-xl border-2 border-[#d84315] bg-white px-3 py-4 text-center text-sm font-bold text-[#d84315] transition-all hover:bg-[#fff3e0] md:px-8 md:text-lg"
                >
                  {isLoggedIn ? '내 매칭 보기' : '회원가입'}
                </Link>
              </div>
            </div>

            <div className="relative">
              <div className="rounded-3xl bg-gradient-to-br from-[#d84315] to-[#bf360c] p-4 shadow-2xl md:p-8">
                <div className="mb-4 rounded-2xl bg-white p-4 md:p-6">
                  <div className="flex items-center gap-3 mb-4">
                    <div className="w-10 h-10 bg-[#f5f5f5] rounded-full flex items-center justify-center">
                      <Users size={20} className="text-[#d84315]" />
                    </div>
                    <div>
                      <p className="font-semibold text-[#212121]">밥먹자</p>
                      <p className="text-xs text-[#9e9e9e]">같이 먹을 사람을 찾는 중</p>
                    </div>
                  </div>
                  <p className="text-[#424242] mb-3">오늘 점심 학생식당 같이 가실 분!</p>
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-[#616161]">📍 학생식당 1층</span>
                    <span className="font-bold text-[#d84315]">3,000P</span>
                  </div>
                </div>
                <div className="bg-white/20 backdrop-blur-sm rounded-2xl p-4 text-white">
                  <p className="text-sm mb-2">✅ 매칭 완료! 채팅이 시작되었습니다</p>
                  <div className="bg-white/20 rounded-lg px-3 py-2 text-sm">
                    "안녕하세요! 12시 30분에 입구에서 만나요 😊"
                  </div>
                </div>
              </div>
              <div className="absolute -top-4 -right-4 w-24 h-24 bg-[#ff9800] rounded-full blur-3xl opacity-50"></div>
              <div className="absolute -bottom-4 -left-4 w-32 h-32 bg-[#d84315] rounded-full blur-3xl opacity-30"></div>
            </div>
          </div>
        </section>

        {/* Features Section */}
        <section className="bg-[#fffaf5] py-20">
          <div className="max-w-screen-xl mx-auto px-6">
            <div className="text-center mb-16">
              <h2 className="text-4xl font-bold text-[#212121] mb-4">왜 한끼팟인가요?</h2>
              <p className="text-lg text-[#616161]">안전하고 확실한 만남을 위한 3가지 핵심 기능</p>
            </div>

            <div className="grid md:grid-cols-3 gap-8">
              <div className="bg-gradient-to-br from-[#fff3e0] to-white rounded-2xl p-8 border border-[#ffe0b2] hover:shadow-xl transition-shadow">
                <div className="w-14 h-14 bg-[#d84315] rounded-2xl flex items-center justify-center mb-6">
                  <Shield size={28} className="text-white" />
                </div>
                <h3 className="text-2xl font-bold text-[#212121] mb-3">책임비 시스템</h3>
                <p className="text-[#616161] leading-relaxed">
                  게시글 작성 시 포인트를 예치하여 노쇼를 방지합니다. 만남 완료 후 전액 반환!
                </p>
              </div>

              <div className="bg-gradient-to-br from-[#e8f5e9] to-white rounded-2xl p-8 border border-[#c8e6c9] hover:shadow-xl transition-shadow">
                <div className="w-14 h-14 bg-[#4caf50] rounded-2xl flex items-center justify-center mb-6">
                  <QrCode size={28} className="text-white" />
                </div>
                <h3 className="text-2xl font-bold text-[#212121] mb-3">QR 만남 인증</h3>
                <p className="text-[#616161] leading-relaxed">
                  실제로 만났는지 QR 코드로 확인합니다. 투명하고 공정한 매칭 시스템!
                </p>
              </div>

              <div className="bg-gradient-to-br from-[#e3f2fd] to-white rounded-2xl p-8 border border-[#bbdefb] hover:shadow-xl transition-shadow">
                <div className="w-14 h-14 bg-[#2196f3] rounded-2xl flex items-center justify-center mb-6">
                  <Utensils size={28} className="text-white" />
                </div>
                <h3 className="text-2xl font-bold text-[#212121] mb-3">학교 인증 커뮤니티</h3>
                <p className="text-[#616161] leading-relaxed">
                  .ac.kr 이메일 인증으로 같은 학교 친구들과만 매칭됩니다. 안전한 만남!
                </p>
              </div>
            </div>
          </div>
        </section>

        {/* How it works */}
        <section className="py-20 bg-gradient-to-br from-[#eef8ff] to-[#fffaf5]">
          <div className="max-w-screen-xl mx-auto px-6">
            <div className="text-center mb-16">
              <h2 className="text-4xl font-bold text-[#212121] mb-4">이용 방법</h2>
              <p className="text-lg text-[#616161]">3단계로 간편하게 식사 메이트를 찾아보세요</p>
            </div>

            <div className="grid md:grid-cols-3 gap-8">
              <div className="relative">
                <div className="text-center">
                  <div className="w-16 h-16 bg-[#d84315] text-white rounded-full flex items-center justify-center text-2xl font-bold mx-auto mb-6 shadow-lg">
                    1
                  </div>
                  <h3 className="text-xl font-bold text-[#212121] mb-3">게시글 작성 또는 신청</h3>
                  <p className="text-[#616161]">
                    먹고 싶은 시간과 장소를 올리거나<br />
                    원하는 게시글에 신청하세요
                  </p>
                </div>
                <div className="hidden md:block absolute top-8 -right-4 w-8 h-0.5 bg-[#e0e0e0]"></div>
              </div>

              <div className="relative">
                <div className="text-center">
                  <div className="w-16 h-16 bg-[#ff9800] text-white rounded-full flex items-center justify-center text-2xl font-bold mx-auto mb-6 shadow-lg">
                    2
                  </div>
                  <h3 className="text-xl font-bold text-[#212121] mb-3">1:1 채팅으로 약속</h3>
                  <p className="text-[#616161]">
                    매칭되면 채팅방이 생성됩니다<br />
                    메뉴와 만날 시간을 정하세요
                  </p>
                </div>
                <div className="hidden md:block absolute top-8 -right-4 w-8 h-0.5 bg-[#e0e0e0]"></div>
              </div>

              <div className="text-center">
                <div className="w-16 h-16 bg-[#4caf50] text-white rounded-full flex items-center justify-center text-2xl font-bold mx-auto mb-6 shadow-lg">
                  3
                </div>
                <h3 className="text-xl font-bold text-[#212121] mb-3">QR 인증 후 만남</h3>
                <p className="text-[#616161]">
                  약속 장소에서 QR 코드로 인증<br />
                  즐거운 식사 시간을 보내세요!
                </p>
              </div>
            </div>
          </div>
        </section>

        {/* CTA Section */}
        <section className="py-20 bg-gradient-to-r from-[#d84315] to-[#bf360c]">
          <div className="max-w-screen-xl mx-auto px-6 text-center">
            <h2 className="text-4xl md:text-5xl font-bold text-white mb-6">
              {isLoggedIn ? '새로운 한끼팟을 찾아보세요' : '지금 바로 시작하세요'}
            </h2>
            <p className="text-xl text-white/90 mb-8">
              {isLoggedIn ? '함께할 사람을 찾고 즐거운 식사를 시작해 보세요' : '회원가입하고 10,000P 보너스 받기'}
            </p>
            <Link
                to={isLoggedIn ? '/posts' : '/signup'}
                className="inline-block px-10 py-4 bg-white text-[#d84315] rounded-xl font-bold text-lg hover:bg-[#f5f5f5] transition-all shadow-xl hover:shadow-2xl hover:scale-105"
            >
              {isLoggedIn ? '게시글 둘러보기 →' : '무료로 시작하기 →'}
            </Link>
          </div>
        </section>

        {/* Footer */}
        <footer className="bg-[#212121] text-white py-12">
          <div className="max-w-screen-xl mx-auto px-6">
            <div className="flex flex-col md:flex-row justify-between items-center gap-6">
              <div className="flex items-center gap-2">
                <span className="text-2xl">🍚</span>
                <span className="text-xl font-bold">한끼팟</span>
              </div>
              <div className="flex gap-6 text-sm text-[#bdbdbd]">
                <a href="#" className="hover:text-white transition-colors">서비스 이용약관</a>
                <a href="#" className="hover:text-white transition-colors">개인정보 처리방침</a>
                <a href="#" className="hover:text-white transition-colors">문의하기</a>
              </div>
            </div>
            <div className="mt-8 pt-6 border-t border-[#424242] text-center text-sm text-[#9e9e9e]">
              © 2026 한끼팟. All rights reserved.
            </div>
          </div>
        </footer>
      </div>
  );
}
