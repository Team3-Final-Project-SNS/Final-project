import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { ArrowLeft, LockKeyhole, ShieldCheck, Sparkles, UsersRound } from 'lucide-react';
import { adminLogin } from '../../api/adminAuthApi';
import adminLoginBackground from '@/assets/images/admin-login-background.png';

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
    <div className="relative flex min-h-[100dvh] overflow-hidden bg-white lg:bg-[#f8f4ee]">
      <img
        src={adminLoginBackground}
        alt=""
        aria-hidden="true"
        className="absolute inset-0 hidden h-full w-full object-cover object-center lg:block"
      />
      <div className="absolute inset-0 hidden bg-[linear-gradient(90deg,rgba(255,255,255,0.02)_0%,rgba(255,255,255,0.08)_42%,rgba(255,255,255,0.36)_57%,rgba(255,255,255,0.78)_100%)] lg:block" />

      <div className="pointer-events-none absolute inset-0 hidden lg:block" aria-hidden="true">
        <div className="hankki-floating-badge left-[9%] top-[22%] delay-0">
          <ShieldCheck size={22} />
        </div>
        <div className="hankki-floating-badge left-[29%] top-[46%] delay-700">
          <UsersRound size={22} />
        </div>
        <div className="hankki-floating-badge left-[50%] top-[30%] delay-300">
          <Sparkles size={22} />
        </div>
      </div>

      <div className="absolute right-[calc(40%+1.5rem)] top-[clamp(1.25rem,5.2vh,4.75rem)] z-10 hidden w-[min(24vw,360px)] flex-col items-end text-right lg:flex">
        <div className="mb-3 flex h-14 w-14 items-center justify-center rounded-full bg-white/80 text-[#b84319] shadow-[0_10px_32px_rgba(83,52,32,0.14)] backdrop-blur-md">
          <ShieldCheck size={30} />
        </div>
        <h1 className="mb-2 text-[clamp(2rem,2.8vw,3.4rem)] font-[850] leading-none tracking-normal text-[#b84319] drop-shadow-[0_2px_14px_rgba(255,255,255,0.82)]">
          한끼팟 Admin
        </h1>
        <p className="whitespace-nowrap text-[clamp(0.78rem,0.9vw,1rem)] font-bold leading-tight text-[#212121] drop-shadow-[0_2px_8px_rgba(255,255,255,0.94)]">
          운영 관리 전용 로그인
        </p>
        <p className="mt-2 max-w-[320px] text-sm font-semibold leading-5 text-[#424242] drop-shadow-[0_2px_8px_rgba(255,255,255,0.94)]">
          신고, 문의, 사용자 관리 기능은 관리자 권한으로만 접근할 수 있습니다.
        </p>
      </div>

      <div className="relative z-10 ml-auto flex w-full items-center justify-center bg-white px-5 py-8 sm:px-8 lg:min-h-[100dvh] lg:w-[40%] lg:border-l lg:border-white/70 lg:bg-white/92 lg:p-12 lg:shadow-[-24px_0_70px_rgba(74,49,30,0.13)] lg:backdrop-blur-md">
        <div className="w-full max-w-[420px]">
          <div className="mb-8 rounded-2xl bg-[#212121] px-5 py-6 text-center shadow-sm lg:hidden">
            <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-[#2e2e2e]">
              <ShieldCheck className="text-[#ff7043]" size={34} />
            </div>
            <h1 className="mb-2 text-3xl font-[850] tracking-normal text-[#b84319]">한끼팟 Admin</h1>
            <p className="text-sm font-semibold text-[#eeeeee]">운영 관리 전용 로그인</p>
          </div>

          <Link
            to="/login"
            className="mb-6 inline-flex items-center gap-2 text-sm font-semibold text-[#616161] transition-colors hover:text-[#d84315] lg:mb-8"
          >
            <ArrowLeft size={18} />
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
                <span className="text-sm text-[#c62828]">{error}</span>
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
