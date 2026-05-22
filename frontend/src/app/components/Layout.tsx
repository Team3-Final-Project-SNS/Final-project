import { useEffect, useState } from 'react';
import { Outlet, Link, useLocation } from 'react-router';
import { User, Bell } from 'lucide-react';
import { getUserMe } from '../../api/userApi';

export default function Layout() {
  const location = useLocation();
  const [point, setPoint] = useState<number | null>(null);

  const isActive = (path: string) => {
    return location.pathname.startsWith(path);
  };

  useEffect(() => {
    const fetchMyPoint = async () => {
      if (!sessionStorage.getItem('accessToken')) {
        setPoint(null);
        return;
      }

      try {
        const res = await getUserMe();
        setPoint(res.data.data.point);
      } catch (err) {
        console.error('Failed to load user point', err);
        setPoint(null);
      }
    };

    fetchMyPoint();
    window.addEventListener('focus', fetchMyPoint);

    return () => {
      window.removeEventListener('focus', fetchMyPoint);
    };
  }, [location.pathname]);

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
            </nav>

            <div className="flex items-center gap-4">
              <div className="flex items-center gap-1 bg-[#fff3e0] px-3 py-1.5 rounded-full">
                <span className="text-[#ef6c00] text-sm">💰</span>
                <span className="text-[#ef6c00] text-sm font-semibold">
                  {point === null ? '-' : `${point.toLocaleString()}P`}
                </span>
              </div>
              <button className="text-[#616161]">
                <Bell size={20} />
              </button>
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
