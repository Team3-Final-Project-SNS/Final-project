import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { AlertCircle, Loader2, LogOut, User } from 'lucide-react';
import { logout } from '../../api/authApi';
import { getUserMe, GetUserResponse } from '../../api/userApi';

export default function MyInfoPage() {
  const navigate = useNavigate();
  const [user, setUser] = useState<GetUserResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const fetchUser = async () => {
      setLoading(true);
      setError('');
      try {
        const res = await getUserMe();
        setUser(res.data.data);
      } catch (err) {
        console.error('Failed to load user info', err);
        setError('내 정보를 불러오는데 실패했습니다.');
      } finally {
        setLoading(false);
      }
    };

    fetchUser();
  }, []);

  const handleLogout = async () => {
    try {
      await logout();
    } catch (err) {
      console.error('Logout request failed', err);
    } finally {
      localStorage.removeItem('accessToken');
      navigate('/');
    }
  };

  if (loading) {
    return (
        <div className="flex flex-col items-center justify-center py-20">
          <Loader2 className="mb-4 animate-spin text-[#d84315]" size={40} />
          <p className="text-[#616161]">내 정보를 불러오는 중...</p>
        </div>
    );
  }

  return (
      <div className="mx-auto max-w-2xl">
        <div className="mb-6 flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold text-[#212121]">내 정보</h1>
            <p className="mt-2 text-sm text-[#757575]">회원 정보와 포인트를 확인할 수 있습니다.</p>
          </div>
          <button
              type="button"
              onClick={handleLogout}
              className="flex items-center gap-2 rounded-lg border border-[#e0e0e0] bg-white px-4 py-2 text-sm font-semibold text-[#616161] transition-colors hover:border-[#d84315] hover:text-[#d84315]"
          >
            <LogOut size={16} />
            로그아웃
          </button>
        </div>

        {error && (
            <div className="mb-6 flex items-start gap-2 rounded-lg border border-[#ef5350] bg-[#ffebee] px-4 py-3 text-sm text-[#c62828]">
              <AlertCircle size={18} className="mt-0.5 shrink-0" />
              <span>{error}</span>
            </div>
        )}

        {user ? (
            <div className="space-y-6">
              <div className="overflow-hidden rounded-2xl border border-[#e0e0e0] bg-white shadow-sm">
                <div className="flex items-center gap-4 border-b border-[#eeeeee] p-6">
                  <div className="flex h-14 w-14 items-center justify-center rounded-full bg-[#eeeeee]">
                    <User size={28} className="text-[#616161]" />
                  </div>
                  <div>
                    <h2 className="text-xl font-bold text-[#212121]">{user.nickname}</h2>
                    <p className="text-sm text-[#757575]">{user.email}</p>
                  </div>
                </div>

                <div className="grid grid-cols-1 gap-px bg-[#eeeeee] sm:grid-cols-2">
                  <InfoItem label="이름" value={user.name} />
                  <InfoItem label="학과" value={user.major} />
                  <InfoItem label="학번" value={user.studentNumber} />
                  <InfoItem label="성별" value={user.gender === 'MALE' ? '남성' : '여성'} />
                  <InfoItem label="보유 포인트" value={`${user.point.toLocaleString()}P`} strong />
                  <InfoItem label="계정 상태" value={user.status} />
                </div>

                <div className="flex flex-wrap justify-end gap-2 p-5">
                  <Link
                      to="/me/points"
                      className="rounded-lg border border-[#d84315] bg-white px-5 py-2.5 text-sm font-semibold text-[#d84315] transition-colors hover:bg-[#fff3e0]"
                  >
                    포인트 거래 내역 조회
                  </Link>
                  <Link
                      to="/me/matches"
                      className="rounded-lg border border-[#d84315] bg-white px-5 py-2.5 text-sm font-semibold text-[#d84315] transition-colors hover:bg-[#fff3e0]"
                  >
                    내 매칭 결과 보기
                  </Link>
                  <Link
                      to="/posts?mine=1"
                      className="rounded-lg bg-[#d84315] px-5 py-2.5 text-sm font-semibold text-white shadow-md transition-colors hover:bg-[#bf360c]"
                  >
                    내가 작성한 게시물 보기
                  </Link>
                </div>
              </div>
            </div>
        ) : (
            <div className="rounded-2xl border border-[#e0e0e0] bg-white p-10 text-center text-[#9e9e9e]">
              표시할 내 정보가 없습니다.
            </div>
        )}
      </div>
  );
}

function InfoItem({ label, value, strong = false }: { label: string; value: string; strong?: boolean }) {
  return (
      <div className="bg-white p-5">
        <p className="mb-1 text-xs font-semibold text-[#9e9e9e]">{label}</p>
        <p className={`text-sm ${strong ? 'font-bold text-[#d84315]' : 'font-semibold text-[#212121]'}`}>
          {value}
        </p>
      </div>
  );
}
