import { Link, useNavigate } from 'react-router';
import { ClipboardList, FileText, LogOut, MessageSquare, ShieldCheck } from 'lucide-react';

export default function AdminHomePage() {
  const navigate = useNavigate();
  const adminName = sessionStorage.getItem('adminName') || '관리자';
  const adminRole = sessionStorage.getItem('adminRole') || 'MANAGER';

  const handleLogout = () => {
    sessionStorage.removeItem('adminAccessToken');
    sessionStorage.removeItem('adminId');
    sessionStorage.removeItem('adminName');
    sessionStorage.removeItem('adminRole');
    navigate('/admin/login');
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-[#fff7ed] via-[#f7fbff] to-[#eaf7f1]">
      <header className="border-b border-[#e0e0e0] bg-white">
        <div className="mx-auto flex h-14 max-w-screen-lg items-center justify-between px-4">
          <div className="flex items-center gap-8">
            <Link to="/admin" className="text-2xl font-bold text-[#d84315]">
              한끼팟 Admin
            </Link>
            <nav className="flex items-center gap-5">
              <Link
                to="/posts"
                className="inline-flex items-center gap-1.5 text-sm font-semibold text-[#424242] transition-colors hover:text-[#d84315]"
              >
                <FileText size={16} />
                게시글
              </Link>
              <Link
                to="/admin/reports"
                className="text-sm font-semibold text-[#424242] transition-colors hover:text-[#d84315]"
              >
                신고
              </Link>
              <Link
                to="/admin/inquiries"
                className="text-sm font-semibold text-[#424242] transition-colors hover:text-[#d84315]"
              >
                문의
              </Link>
            </nav>
          </div>
          <button
            type="button"
            onClick={handleLogout}
            className="inline-flex items-center gap-2 rounded-lg border border-[#e0e0e0] px-4 py-2 text-sm font-semibold text-[#616161] transition-colors hover:border-[#d84315] hover:text-[#d84315]"
          >
            <LogOut size={16} />
            로그아웃
          </button>
        </div>
      </header>

      <main className="mx-auto max-w-screen-lg px-4 py-10">
        <div className="mb-8 flex items-center gap-4">
          <div className="flex h-14 w-14 items-center justify-center rounded-full bg-[#fff3e0]">
            <ShieldCheck className="text-[#d84315]" size={30} />
          </div>
          <div>
            <h1 className="text-3xl font-bold text-[#212121]">관리자 콘솔</h1>
            <p className="mt-1 text-sm text-[#757575]">
              {adminName}님, {adminRole} 권한으로 로그인했습니다.
            </p>
          </div>
        </div>

        <div className="grid gap-4 sm:grid-cols-3">
          <Link
            to="/posts"
            className="rounded-2xl border border-[#e0e0e0] bg-white p-6 shadow-sm transition-all hover:border-[#d84315] hover:shadow-md"
          >
            <FileText className="mb-4 text-[#d84315]" size={32} />
            <h2 className="text-lg font-bold text-[#212121]">게시글 보기</h2>
            <p className="mt-2 text-sm leading-6 text-[#757575]">등록된 게시글 목록과 상세 내용을 확인합니다.</p>
          </Link>

          <Link
            to="/admin/reports"
            className="rounded-2xl border border-[#e0e0e0] bg-white p-6 shadow-sm transition-all hover:border-[#d84315] hover:shadow-md"
          >
            <ClipboardList className="mb-4 text-[#d84315]" size={32} />
            <h2 className="text-lg font-bold text-[#212121]">신고 관리</h2>
            <p className="mt-2 text-sm leading-6 text-[#757575]">접수된 신고를 확인하고 처리합니다.</p>
          </Link>

          <Link
            to="/admin/inquiries"
            className="rounded-2xl border border-[#e0e0e0] bg-white p-6 shadow-sm transition-all hover:border-[#d84315] hover:shadow-md"
          >
            <MessageSquare className="mb-4 text-[#d84315]" size={32} />
            <h2 className="text-lg font-bold text-[#212121]">고객 문의 관리</h2>
            <p className="mt-2 text-sm leading-6 text-[#757575]">고객 문의를 확인하고 답변합니다.</p>
          </Link>
        </div>
      </main>
    </div>
  );
}
