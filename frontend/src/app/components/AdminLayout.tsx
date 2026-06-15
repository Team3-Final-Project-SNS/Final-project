import {
  ClipboardList,
  CreditCard,
  FileQuestion,
  FileText,
  Gavel,
  LayoutDashboard,
  LogOut,
  Menu,
  MessageSquare,
  X,
  Users,
} from 'lucide-react';
import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router';
import AdminFloatingChatbot from './AdminFloatingChatbot';
import AdminNotificationBell from './AdminNotificationBell';

const menuItems = [
  { to: '/admin', label: '대시보드', icon: LayoutDashboard, end: true },
  { to: '/admin/posts', label: '게시글 보기', icon: FileText },
  { to: '/admin/reports', label: '신고 관리', icon: ClipboardList },
  { to: '/admin/inquiries', label: '고객 문의 관리', icon: MessageSquare },
  { to: '/admin/disputes', label: '이의제기 관리', icon: Gavel },
  { to: '/admin/users', label: '유저 목록', icon: Users },
  { to: '/admin/payments', label: '결제 내역 관리', icon: CreditCard },
  { to: '/admin/faq', label: 'FAQ', icon: FileQuestion },
];

const bottomItems = [
  { to: '/admin', label: '대시보드', icon: LayoutDashboard, end: true },
  { to: '/admin/reports', label: '신고관리', icon: ClipboardList },
  { to: '/admin/inquiries', label: '고객문의', icon: MessageSquare },
  { to: '/admin/disputes', label: '이의제기', icon: Gavel },
  { to: '/admin/payments', label: '결제내역', icon: CreditCard },
];

const isJwtExpired = (token: string) => {
  try {
    const payload = JSON.parse(atob(token.split('.')[1] || '')) as { exp?: number };
    return !payload.exp || payload.exp * 1000 <= Date.now();
  } catch {
    return true;
  }
};

export default function AdminLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);
  const adminName = sessionStorage.getItem('adminName') || '관리자';
  const adminRole = sessionStorage.getItem('adminRole') || 'MANAGER';

  // [추가] 페이지 진입/새로고침 시 관리자 토큰 존재 여부 확인
  // - 토큰이 없으면 로그인 페이지로 즉시 이동
  // - axiosInstance의 401 처리는 "API 호출이 일어나야" 감지되므로,
  //   API 호출이 없는 페이지(또는 진입 즉시)에서도 보호되도록 여기서 1차 체크
  useEffect(() => {
    const adminAccessToken = sessionStorage.getItem('adminAccessToken');
    if (!adminAccessToken || isJwtExpired(adminAccessToken)) {
      sessionStorage.removeItem('adminAccessToken');
      sessionStorage.removeItem('adminId');
      sessionStorage.removeItem('adminName');
      sessionStorage.removeItem('adminRole');
      navigate('/admin/login', { replace: true });
    }
  }, [navigate]);

  useEffect(() => {
    setMenuOpen(false);
    window.scrollTo({ top: 0, left: 0, behavior: 'auto' });
  }, [location.pathname]);

  const handleLogout = () => {
    sessionStorage.removeItem('adminAccessToken');
    sessionStorage.removeItem('adminId');
    sessionStorage.removeItem('adminName');
    sessionStorage.removeItem('adminRole');
    navigate('/admin/login');
  };

  return (
      <div className="min-h-screen bg-gradient-to-br from-[#fff7ed] via-[#f7fbff] to-[#eaf7f1]">
        <header className="sticky top-0 z-50 border-b border-[#e0e0e0] bg-white">
          <div className="hidden h-16 items-center justify-between px-6 md:flex">
            <NavLink to="/admin" end className="text-2xl font-bold text-[#d84315]">
              한끼팟 Admin
            </NavLink>
            <div className="flex items-center gap-5">
              <AdminNotificationBell />
              <button
                  type="button"
                  onClick={handleLogout}
                  className="inline-flex items-center gap-2 rounded-lg border border-[#e0e0e0] px-4 py-2 text-sm font-semibold text-[#616161] transition-colors hover:border-[#d84315] hover:text-[#d84315]"
              >
                <LogOut size={16} />
                로그아웃
              </button>
            </div>
          </div>

          <div className="flex h-16 items-center justify-between px-4 md:hidden">
            <button
                type="button"
                onClick={() => setMenuOpen((current) => !current)}
                className="flex h-9 w-9 items-center justify-center rounded-lg text-[#616161] hover:bg-[#fff3e0] hover:text-[#d84315]"
                aria-label={menuOpen ? '관리자 메뉴 닫기' : '관리자 메뉴 열기'}
                aria-expanded={menuOpen}
            >
              {menuOpen ? <X size={23} /> : <Menu size={23} />}
            </button>
            <NavLink to="/admin" end className="text-xl font-bold text-[#d84315]">
              한끼팟 Admin
            </NavLink>
            <AdminNotificationBell />
          </div>
        </header>

        <div className="flex min-h-[calc(100vh-4rem)]">
          <aside className="sticky top-16 hidden h-[calc(100vh-4rem)] w-64 shrink-0 flex-col overflow-y-auto border-r border-[#e0e0e0] bg-white/95 px-4 py-4 md:flex">
            <div className="mb-4 rounded-2xl bg-[#fff7ed] px-4 py-3">
              <p className="font-bold text-[#212121]">{adminName}</p>
              <p className="mt-1 text-xs font-semibold text-[#d84315]">{adminRole}</p>
            </div>

            <nav className="space-y-1">
              {menuItems.map(({ to, label, icon: Icon, end }) => (
                  <NavLink
                      key={to}
                      to={to}
                      end={end}
                      className={({ isActive }) =>
                          `flex items-center gap-3 rounded-xl px-4 py-2.5 text-sm font-bold transition-colors ${
                              isActive
                                  ? 'bg-[#d84315] text-white shadow-sm'
                                  : 'text-[#616161] hover:bg-[#fff3e0] hover:text-[#d84315]'
                          }`
                      }
                  >
                    <Icon size={19} />
                    {label}
                  </NavLink>
              ))}
            </nav>

          </aside>

          <main className="min-w-0 flex-1 overflow-x-hidden p-4 pb-24 md:p-6 md:pb-6 lg:p-8">
            <Outlet />
          </main>
        </div>

        {menuOpen && (
            <>
              <button
                  type="button"
                  aria-label="관리자 메뉴 닫기"
                  onClick={() => setMenuOpen(false)}
                  className="fixed inset-0 top-16 z-40 bg-black/25 md:hidden"
              />
              <aside className="fixed bottom-0 left-0 top-16 z-[60] flex w-[78%] max-w-[300px] flex-col bg-white px-4 py-5 shadow-[10px_0_30px_rgba(0,0,0,0.14)] md:hidden">
                <div className="mb-4 rounded-2xl bg-[#fff7ed] px-4 py-3">
                  <p className="font-bold text-[#212121]">{adminName}</p>
                  <p className="mt-1 text-xs font-semibold text-[#d84315]">{adminRole}</p>
                </div>

                <nav className="space-y-1 overflow-y-auto">
                  {menuItems.map(({ to, label, icon: Icon, end }) => (
                      <NavLink
                          key={to}
                          to={to}
                          end={end}
                          className={({ isActive }) =>
                              `flex items-center gap-3 rounded-xl px-4 py-3 text-sm font-bold ${
                                  isActive ? 'bg-[#d84315] text-white' : 'text-[#616161] hover:bg-[#fff3e0]'
                              }`
                          }
                      >
                        <Icon size={19} />
                        {label}
                      </NavLink>
                  ))}
                </nav>

                <button
                    type="button"
                    onClick={handleLogout}
                    className="mt-auto flex items-center justify-center gap-2 border-t border-[#eeeeee] pt-4 text-sm font-semibold text-[#757575]"
                >
                  <LogOut size={16} />
                  로그아웃
                </button>
              </aside>
            </>
        )}

        <nav className="fixed bottom-0 left-0 right-0 z-50 grid h-[70px] grid-cols-5 border-t border-[#dedede] bg-white px-1 pb-1 pt-1.5 shadow-[0_-5px_16px_rgba(0,0,0,0.06)] md:hidden">
          {bottomItems.map(({ to, label, icon: Icon, end }) => (
              <NavLink
                  key={to}
                  to={to}
                  end={end}
                  className={({ isActive }) =>
                      `flex min-w-0 flex-col items-center justify-center gap-1 rounded-lg text-[10px] font-bold ${
                          isActive ? 'text-[#d84315]' : 'text-[#757575]'
                      }`
                  }
              >
                <Icon size={20} />
                <span className="whitespace-nowrap">{label}</span>
              </NavLink>
          ))}
        </nav>

        <AdminFloatingChatbot />
      </div>
  );
}
