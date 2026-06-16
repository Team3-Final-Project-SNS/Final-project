import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { AlertCircle, Loader2, LogOut, Trash2, User } from 'lucide-react';
import { toast } from 'sonner';
import { logout } from '@/api/authApi';
import { getUserMe, GetUserResponse, withdrawUserMe } from '@/api/userApi';
import { clearAccessToken } from '@/api/axiosInstance';
import { setUserStatus, useAuthStatus } from '@/store/authStatusStore';

export default function MyInfoPage() {
  const navigate = useNavigate();
  const [user, setUser] = useState<GetUserResponse | null>(null);
  const [mannerTemperature, setMannerTemperature] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [withdrawOpen, setWithdrawOpen] = useState(false);
  const [withdrawPassword, setWithdrawPassword] = useState('');
  const [withdrawError, setWithdrawError] = useState('');
  const [withdrawing, setWithdrawing] = useState(false);
  const { isSuspended } = useAuthStatus();
  const handleSuspendedLinkClick = (event: React.MouseEvent<HTMLAnchorElement>) => {
    event.preventDefault();
    toast.warning('정지된 계정입니다. 문의하기로 이의를 제기해 주세요.');
  };

  useEffect(() => {
    const fetchUser = async () => {
      setLoading(true);
      setError('');
      try {
        const res = await getUserMe();
        const me = res.data.data;
        setUser(me);
        setUserStatus(me.status);
        setMannerTemperature(me.mannerTemperature);
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
      clearAccessToken();
      navigate('/');
    }
  };

  const openWithdrawModal = () => {
    setWithdrawPassword('');
    setWithdrawError('');
    setWithdrawOpen(true);
  };

  const closeWithdrawModal = () => {
    if (withdrawing) return;
    setWithdrawOpen(false);
    setWithdrawPassword('');
    setWithdrawError('');
  };

  const handleWithdraw = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!withdrawPassword.trim()) {
      setWithdrawError('현재 비밀번호를 입력해주세요.');
      return;
    }

    setWithdrawing(true);
    setWithdrawError('');

    try {
      await withdrawUserMe({ password: withdrawPassword });
      clearAccessToken();
      toast.success('회원 탈퇴가 완료되었습니다.');
      navigate('/');
    } catch (err: any) {
      const message = err.response?.data?.message || '회원 탈퇴에 실패했습니다.';
      setWithdrawError(
        err.response?.data?.code === 'USER_002'
          ? '비밀번호가 일치하지 않습니다.'
          : message
      );
    } finally {
      setWithdrawing(false);
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
          <div className="flex items-center gap-2">
            <Link
                to="/me/edit"
                onClick={isSuspended ? handleSuspendedLinkClick : undefined}
                aria-disabled={isSuspended}
                className="rounded-lg border border-[#d84315] bg-white px-4 py-2 text-sm font-semibold text-[#d84315] transition-colors hover:bg-[#fff3e0]"
            >
              내정보 수정하기
            </Link>
            <button
                type="button"
                onClick={handleLogout}
                className="flex items-center gap-2 rounded-lg border border-[#e0e0e0] bg-white px-4 py-2 text-sm font-semibold text-[#616161] transition-colors hover:border-[#d84315] hover:text-[#d84315]"
            >
              <LogOut size={16} />
              로그아웃
            </button>
          </div>
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
                  <InfoItem
                      label="매너 온도"
                      value={mannerTemperature === null ? '-' : `${Number(mannerTemperature).toFixed(1)}°C`}
                      strong
                  />
                </div>

                <div className="flex flex-wrap justify-end gap-2 p-5">
                  <Link
                      to="/me/points"
                      onClick={isSuspended ? handleSuspendedLinkClick : undefined}
                      aria-disabled={isSuspended}
                      className="rounded-lg border border-[#d84315] bg-white px-5 py-2.5 text-sm font-semibold text-[#d84315] transition-colors hover:bg-[#fff3e0]"
                  >
                    포인트 거래 내역 조회
                  </Link>
                  <Link
                      to="/payments"
                      onClick={isSuspended ? handleSuspendedLinkClick : undefined}
                      aria-disabled={isSuspended}
                      className="rounded-lg border border-[#d84315] bg-white px-5 py-2.5 text-sm font-semibold text-[#d84315] transition-colors hover:bg-[#fff3e0]"
                  >
                    내 결제 보기
                  </Link>
                  <Link
                      to="/me/matches"
                      onClick={isSuspended ? handleSuspendedLinkClick : undefined}
                      aria-disabled={isSuspended}
                      className="rounded-lg border border-[#d84315] bg-white px-5 py-2.5 text-sm font-semibold text-[#d84315] transition-colors hover:bg-[#fff3e0]"
                  >
                    내 매칭 결과 보기
                  </Link>
                  <Link
                      to="/posts?mine=1"
                      onClick={isSuspended ? handleSuspendedLinkClick : undefined}
                      aria-disabled={isSuspended}
                      className="rounded-lg bg-[#d84315] px-5 py-2.5 text-sm font-semibold text-white shadow-md transition-colors hover:bg-[#bf360c]"
                  >
                    내가 작성한 게시물 보기
                  </Link>
                </div>

                <div className="flex justify-end border-t border-[#eeeeee] px-5 py-4">
                  <button
                      type="button"
                      onClick={openWithdrawModal}
                      className="inline-flex h-10 items-center justify-center gap-2 rounded-lg border border-[#e0e0e0] bg-white px-4 text-sm font-semibold text-[#757575] transition-colors hover:border-[#c62828] hover:bg-[#ffebee] hover:text-[#c62828]"
                  >
                    <Trash2 size={15} />
                    회원 탈퇴
                  </button>
                </div>
              </div>
            </div>
        ) : (
            <div className="rounded-2xl border border-[#e0e0e0] bg-white p-10 text-center text-[#9e9e9e]">
              표시할 내 정보가 없습니다.
            </div>
        )}

        {withdrawOpen && (
            <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/45 p-4">
              <form
                  onSubmit={handleWithdraw}
                  className="w-full max-w-md rounded-2xl border border-[#eeeeee] bg-white p-6 shadow-xl"
              >
                <div className="mb-5">
                  <h2 className="text-xl font-bold text-[#212121]">회원 탈퇴</h2>
                  <p className="mt-2 text-sm leading-6 text-[#616161]">
                    회원 탈퇴를 진행하려면 현재 비밀번호를 입력해주세요.
                  </p>
                </div>

                <label className="block">
                  <span className="mb-2 block text-sm font-bold text-[#616161]">현재 비밀번호</span>
                  <input
                      type="password"
                      value={withdrawPassword}
                      onChange={(event) => {
                        setWithdrawPassword(event.target.value);
                        if (withdrawError) setWithdrawError('');
                      }}
                      className={`h-12 w-full rounded-lg border px-4 text-sm font-semibold outline-none transition-colors focus:border-[#d84315] ${
                        withdrawError ? 'border-[#ef5350] bg-[#fffafa]' : 'border-[#e0e0e0] bg-white'
                      }`}
                      placeholder="현재 비밀번호"
                      autoFocus
                  />
                </label>

                {withdrawError && (
                    <p className="mt-2 text-sm font-semibold text-[#c62828]">{withdrawError}</p>
                )}

                <div className="mt-6 flex justify-end gap-2">
                  <button
                      type="button"
                      onClick={closeWithdrawModal}
                      disabled={withdrawing}
                      className="inline-flex h-11 items-center justify-center rounded-lg border border-[#e0e0e0] bg-white px-5 text-sm font-bold text-[#616161] transition-colors hover:bg-[#f5f5f5] disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    취소
                  </button>
                  <button
                      type="submit"
                      disabled={withdrawing}
                      className="inline-flex h-11 items-center justify-center gap-2 rounded-lg bg-[#d84315] px-5 text-sm font-bold text-white shadow-sm transition-colors hover:bg-[#bf360c] disabled:cursor-not-allowed disabled:bg-[#ffab91]"
                  >
                    {withdrawing ? <Loader2 className="animate-spin" size={17} /> : <Trash2 size={17} />}
                    탈퇴하기
                  </button>
                </div>
              </form>
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
