import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { School, HandHeart, Lock } from 'lucide-react';

export default function LoginPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');

  const handleLogin = (e: React.FormEvent) => {
    e.preventDefault();
    navigate('/');
  };

  return (
      <div className="min-h-screen bg-[#fafafa] flex">
        <div className="w-1/2 bg-[#212121] flex flex-col items-center justify-center p-12">
          <h1 className="text-5xl font-bold text-[#d84315] mb-4">한끼팟</h1>
          <p className="text-[#e0e0e0] text-lg mb-12">학교 친구와 함께하는</p>
          <p className="text-[#e0e0e0] text-lg">한 끼 식사 매칭 서비스</p>

          <div className="mt-16 flex gap-8">
            <div className="flex flex-col items-center gap-2">
              <div className="w-12 h-12 bg-[#424242] rounded-full flex items-center justify-center">
                <School className="text-white" size={24} />
              </div>
              <span className="text-[#bdbdbd] text-sm">학교 인증</span>
            </div>
            <div className="flex flex-col items-center gap-2">
              <div className="w-12 h-12 bg-[#424242] rounded-full flex items-center justify-center">
                <HandHeart className="text-white" size={24} />
              </div>
              <span className="text-[#bdbdbd] text-sm">1:1 매칭</span>
            </div>
            <div className="flex flex-col items-center gap-2">
              <div className="w-12 h-12 bg-[#424242] rounded-full flex items-center justify-center">
                <Lock className="text-white" size={24} />
              </div>
              <span className="text-[#bdbdbd] text-sm">책임비 시스템</span>
            </div>
          </div>
        </div>

        <div className="w-1/2 bg-white flex items-center justify-center p-12">
          <div className="w-full max-w-md">
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
          </div>
        </div>
      </div>
  );
}
