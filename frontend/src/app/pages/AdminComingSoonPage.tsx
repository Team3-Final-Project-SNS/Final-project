import { Link, useLocation } from 'react-router';
import { ArrowLeft, Hammer } from 'lucide-react';
import AdminFloatingChatbot from '../components/AdminFloatingChatbot';

const pageTitles: Record<string, string> = {
  '/admin/payments': '주문 결제 관리',
  '/admin/faq': 'FAQ',
};

export default function AdminComingSoonPage() {
  const location = useLocation();
  const title = pageTitles[location.pathname] || '관리자 기능';

  return (
    <div className="min-h-screen bg-gradient-to-br from-[#fff7ed] via-[#f7fbff] to-[#eaf7f1]">
      <main className="mx-auto max-w-screen-lg px-4 py-10">
        <Link to="/admin" className="mb-4 inline-flex items-center gap-1 text-sm font-semibold text-[#616161] hover:text-[#d84315]">
          <ArrowLeft size={16} />
          관리자 콘솔
        </Link>

        <div className="rounded-2xl border border-[#e0e0e0] bg-white p-12 text-center shadow-sm">
          <div className="mx-auto mb-5 flex h-16 w-16 items-center justify-center rounded-full bg-[#fff3e0]">
            <Hammer className="text-[#d84315]" size={34} />
          </div>
          <h1 className="text-3xl font-bold text-[#212121]">{title}</h1>
          <p className="mt-3 text-sm font-semibold text-[#757575]">구현중입니다.</p>
        </div>
      </main>
      <AdminFloatingChatbot />
    </div>
  );
}
