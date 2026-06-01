import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { ArrowLeft, LockKeyhole, ShieldCheck } from 'lucide-react';
import { adminLogin } from '../../api/adminAuthApi';

export default function AdminLoginPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleLogin = async (event: React.FormEvent) => {
    event.preventDefault();
    setError('');
    setLoading(true);

    try {
      const res = await adminLogin(email, password);
      const { adminAccessToken, adminId, name, role } = res.data.data;

      sessionStorage.removeItem('accessToken');
      sessionStorage.setItem('adminAccessToken', adminAccessToken);
      sessionStorage.setItem('adminId', String(adminId));
      sessionStorage.setItem('adminName', name);
      sessionStorage.setItem('adminRole', role);

      navigate('/admin');
    } catch (err: any) {
      setError(err.response?.data?.message || '관리자 로그인에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#fafafa] flex">
      <div className="w-1/2 bg-[#1b1b1b] flex flex-col items-center justify-center p-12">
        <div className="mb-6 flex h-20 w-20 items-center justify-center rounded-full bg-[#2e2e2e]">
          <ShieldCheck className="text-[#ff7043]" size={42} />
        </div>
        <h1 className="mb-4 text-5xl font-bold text-[#ff7043]">한끼팟 Admin</h1>
        <p className="text-lg text-[#e0e0e0]">운영 관리 전용 로그인</p>
        <p className="mt-3 max-w-sm text-center text-sm leading-6 text-[#9e9e9e]">
          신고, 문의, 사용자 관리 기능은 관리자 권한으로만 접근할 수 있습니다.
        </p>
      </div>

      <div className="w-1/2 bg-white flex items-center justify-center p-12">
        <div className="w-full max-w-md">
          <Link
            to="/login"
            className="mb-8 inline-flex items-center gap-1 text-sm font-semibold text-[#616161] transition-colors hover:text-[#d84315]"
          >
            <ArrowLeft size={16} />
            일반 로그인으로 돌아가기
          </Link>

          <h2 className="mb-2 text-2xl font-bold text-[#212121]">관리자 로그인</h2>
          <p className="mb-8 text-sm text-[#616161]">관리자 계정으로 로그인하세요.</p>

          <form onSubmit={handleLogin} className="space-y-4">
            <div>
              <label className="mb-2 block text-sm font-medium text-[#424242]">관리자 이메일</label>
              <input
                type="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                placeholder="admin@hankki-pot.com"
                className="w-full rounded-lg border border-[#e0e0e0] px-4 py-3 focus:border-transparent focus:outline-none focus:ring-2 focus:ring-[#d84315]"
              />
            </div>

            <div>
              <label className="mb-2 block text-sm font-medium text-[#424242]">비밀번호</label>
              <input
                type="password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                placeholder="••••••••"
                className="w-full rounded-lg border border-[#e0e0e0] px-4 py-3 focus:border-transparent focus:outline-none focus:ring-2 focus:ring-[#d84315]"
              />
            </div>

            {error && (
              <div className="rounded-lg border border-[#ef5350] bg-[#ffebee] px-4 py-3">
                <span className="text-sm text-[#c62828]">⚠️ {error}</span>
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="flex w-full items-center justify-center gap-2 rounded-xl bg-[#d84315] py-4 font-bold text-white shadow-md transition-all hover:bg-[#bf360c] hover:shadow-lg disabled:opacity-60"
            >
              <LockKeyhole size={18} />
              {loading ? '로그인 중...' : '관리자 로그인'}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}
