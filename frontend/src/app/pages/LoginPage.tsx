import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { ArrowLeft } from 'lucide-react';
import { login } from '@/api/authApi';
import { getUserMe } from '@/api/userApi';
import { clearAccessToken, markLoginRestoreHint, setAccessToken } from '@/api/axiosInstance';
import { setUserStatus } from '@/store/authStatusStore';
import { toast } from 'sonner';
import loginBackground from '@/assets/images/login-background.png';

export default function LoginPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    try {
      const res = await login(email, password);
      const { accessToken } = res.data.data;

      // accessToken을 sessionStorage에 저장 → 브라우저 세션 동안만 로그인 유지
      sessionStorage.removeItem("adminAccessToken");
      sessionStorage.removeItem("adminId");
      sessionStorage.removeItem("adminName");
      sessionStorage.removeItem("adminRole");
      // sessionStorage -> 메모리 저장
      // clearAccessToken: 이전 유저 토큰이 혹시 남아있으면 먼저 비우기
      // setAccessToken: axiosInstance 모듈 변수에 저장 → 탭이 살아있는 동안 유지
      clearAccessToken();
      setAccessToken(accessToken);
      markLoginRestoreHint();

      const meRes = await getUserMe();
      const { status } = meRes.data.data;
      setUserStatus(status);

      if (status === 'SUSPENDED') {
        toast.warning('계정이 정지된 상태입니다. 문의하기를 통해 이의를 제기할 수 있습니다.');
        navigate('/me');
        return;
      }

      navigate('/');
    } catch (err: any) {
      setError(err.response?.data?.message || '로그인에 실패했습니다.');
    }
  };

  return (
      <div className="relative flex min-h-[100dvh] overflow-hidden bg-white lg:bg-[#f8f4ee]">
        <img
            src={loginBackground}
            alt=""
            aria-hidden="true"
            className="absolute inset-0 hidden h-full w-full object-cover object-center lg:block"
        />
        <div className="absolute inset-0 hidden bg-[linear-gradient(90deg,rgba(255,255,255,0.02)_0%,rgba(255,255,255,0.08)_44%,rgba(255,255,255,0.38)_58%,rgba(255,255,255,0.76)_100%)] lg:block" />

        <div className="absolute right-[calc(40%+1.5rem)] top-[clamp(1.25rem,5.2vh,4.75rem)] z-10 hidden w-[min(24vw,330px)] flex-col items-end text-right lg:flex">
          <h1 className="mb-2 text-[clamp(2.25rem,3.2vw,3.8rem)] font-bold leading-none text-[#d84315] drop-shadow-[0_2px_8px_rgba(255,255,255,0.9)]">한끼팟</h1>
          <p className="whitespace-nowrap text-[clamp(0.78rem,0.9vw,1rem)] font-bold leading-tight text-[#212121] drop-shadow-[0_2px_8px_rgba(255,255,255,0.92)]">학교 친구와 함께하는 한 끼 식사 매칭 서비스</p>
        </div>

        <div className="relative z-10 ml-auto flex w-full items-center justify-center bg-white px-5 py-8 sm:px-8 lg:min-h-[100dvh] lg:w-[40%] lg:border-l lg:border-white/70 lg:bg-white/92 lg:p-12 lg:shadow-[-24px_0_70px_rgba(74,49,30,0.13)] lg:backdrop-blur-md">
          <div className="w-full max-w-[420px]">
            <div className="mb-8 rounded-2xl bg-[#212121] px-5 py-6 text-center shadow-sm lg:hidden">
              <h1 className="mb-2 text-3xl font-bold text-[#d84315]">한끼팟</h1>
              <p className="text-sm font-semibold text-[#eeeeee]">학교 친구와 함께하는 한 끼 식사 매칭 서비스</p>
            </div>

            <button
                type="button"
                onClick={() => navigate('/')}
                className="mb-6 inline-flex items-center gap-2 text-sm font-semibold text-[#616161] transition-colors hover:text-[#d84315] lg:mb-8"
            >
              <ArrowLeft size={18} />
              홈으로 돌아가기
            </button>

            <h2 className="text-2xl font-bold text-[#212121] mb-2">로그인</h2>
            <p className="text-[#616161] text-sm mb-8">학교 이메일로 로그인하세요</p>

            <form onSubmit={handleLogin} className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-[#424242] mb-2">
                  이메일
                </label>
                <input
                    type="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="hong@university.ac.kr"
                    className="w-full px-4 py-3 border border-[#e0e0e0] rounded-lg focus:outline-none focus:ring-2 focus:ring-[#d84315] focus:border-transparent"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-[#424242] mb-2">
                  비밀번호
                </label>
                <input
                    type="password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="••••••••"
                    className="w-full px-4 py-3 border border-[#e0e0e0] rounded-lg focus:outline-none focus:ring-2 focus:ring-[#d84315] focus:border-transparent"
                />
              </div>

              {error && (
                  <div className="bg-[#ffebee] border border-[#ef5350] rounded-lg px-4 py-3 flex items-start gap-2">
                    <span className="text-[#c62828] text-sm">⚠️ {error}</span>
                  </div>
              )}

              <button
                  type="submit"
                  className="w-full bg-[#d84315] text-white py-4 rounded-xl font-bold hover:bg-[#bf360c] transition-all shadow-md hover:shadow-lg"
              >
                로그인
              </button>
            </form>

            <div className="mt-6 text-center">
              <span className="text-[#616161] text-sm">계정이 없으신가요? </span>
              <Link to="/signup" className="text-[#d84315] text-sm font-semibold hover:underline">
                회원가입
              </Link>
            </div>

            <div className="mt-6 border-t border-[#eeeeee] pt-5 text-center">
              <p className="mb-3 text-xs font-semibold text-[#9e9e9e]">운영자 계정으로 접속하시나요?</p>
              <Link
                  to="/admin/login"
                  className="inline-flex w-full items-center justify-center rounded-xl border border-[#d84315] bg-white px-4 py-3 text-sm font-bold text-[#d84315] transition-colors hover:bg-[#fff3e0]"
              >
                관리자 로그인으로 전환
              </Link>
            </div>
          </div>
        </div>
      </div>
  );
}
