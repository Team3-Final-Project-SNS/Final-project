import { Outlet, Link, useLocation } from 'react-router';
import { User, Bell } from 'lucide-react';

export default function Layout() {
  const location = useLocation();

  const isActive = (path: string) => {
    return location.pathname.startsWith(path);
  };

  return (
    <div className="min-h-screen bg-[#fafafa]">
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
              <span className="text-[#ef6c00] text-sm font-semibold">5,500P</span>
            </div>
            <button className="text-[#616161]">
              <Bell size={20} />
            </button>
            <button className="w-8 h-8 bg-[#e0e0e0] rounded-full flex items-center justify-center">
              <User size={18} className="text-[#616161]" />
            </button>
          </div>
        </div>
      </header>

      <main className="max-w-screen-lg mx-auto px-4 py-6">
        <Outlet />
      </main>
    </div>
  );
}
