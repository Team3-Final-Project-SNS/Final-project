import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { AlertCircle, ArrowLeft, Loader2, Save } from 'lucide-react';
import { getUserMe, updateUserMe } from '../../api/userApi';

export default function MyInfoEditPage() {
  const navigate = useNavigate();
  const [nickname, setNickname] = useState('');
  const [major, setMajor] = useState('');
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const fetchUser = async () => {
      setLoading(true);
      setError('');

      try {
        const response = await getUserMe();
        const user = response.data.data;
        setNickname(user.nickname);
        setMajor(user.major);
      } catch (err) {
        console.error('Failed to load user info', err);
        setError('내 정보를 불러오는데 실패했습니다.');
      } finally {
        setLoading(false);
      }
    };

    fetchUser();
  }, []);

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!nickname.trim() || !major.trim()) {
      setError('닉네임과 학과를 입력해주세요.');
      return;
    }

    if ((currentPassword || newPassword) && (!currentPassword || !newPassword)) {
      setError('비밀번호를 변경하려면 현재 비밀번호와 새 비밀번호를 모두 입력해주세요.');
      return;
    }

    setSaving(true);
    setError('');

    try {
      const payload: Parameters<typeof updateUserMe>[0] = {
        nickname: nickname.trim(),
        major: major.trim(),
      };
      if (currentPassword) payload.currentPassword = currentPassword;
      if (newPassword) payload.newPassword = newPassword;

      await updateUserMe(payload);
      alert('내 정보가 수정되었습니다.');
      navigate('/me');
    } catch (err: any) {
      setError(err.response?.data?.message || '내 정보 수정에 실패했습니다.');
    } finally {
      setSaving(false);
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
      <Link to="/me" className="mb-5 inline-flex items-center gap-1 text-sm font-semibold text-[#616161] hover:text-[#d84315]">
        <ArrowLeft size={16} />
        내 정보
      </Link>

      <div className="mb-6">
        <h1 className="text-3xl font-bold text-[#212121]">내정보 수정</h1>
        <p className="mt-2 text-sm text-[#757575]">닉네임, 학과, 비밀번호를 수정할 수 있습니다.</p>
      </div>

      {error && (
        <div className="mb-6 flex items-start gap-2 rounded-lg border border-[#ef5350] bg-[#ffebee] px-4 py-3 text-sm text-[#c62828]">
          <AlertCircle size={18} className="mt-0.5 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      <form onSubmit={handleSubmit} className="rounded-2xl border border-[#e0e0e0] bg-white p-4 shadow-sm sm:p-6">
        <div className="space-y-5">
          <FormField label="닉네임">
            <input
              value={nickname}
              onChange={(event) => setNickname(event.target.value)}
              className="h-12 w-full rounded-lg border border-[#e0e0e0] px-4 text-sm font-semibold outline-none focus:border-[#d84315]"
              placeholder="닉네임"
            />
          </FormField>

          <FormField label="학과">
            <input
              value={major}
              onChange={(event) => setMajor(event.target.value)}
              className="h-12 w-full rounded-lg border border-[#e0e0e0] px-4 text-sm font-semibold outline-none focus:border-[#d84315]"
              placeholder="학과"
            />
          </FormField>

          <div className="rounded-xl bg-[#fafafa] p-4">
            <p className="mb-3 text-sm font-bold text-[#424242]">비밀번호 변경</p>
            <div className="grid gap-3 sm:grid-cols-2">
              <input
                type="password"
                value={currentPassword}
                onChange={(event) => setCurrentPassword(event.target.value)}
                className="h-12 rounded-lg border border-[#e0e0e0] px-4 text-sm font-semibold outline-none focus:border-[#d84315]"
                placeholder="현재 비밀번호"
              />
              <input
                type="password"
                value={newPassword}
                onChange={(event) => setNewPassword(event.target.value)}
                className="h-12 rounded-lg border border-[#e0e0e0] px-4 text-sm font-semibold outline-none focus:border-[#d84315]"
                placeholder="새 비밀번호"
              />
            </div>
          </div>
        </div>

        <div className="mt-6 flex justify-end gap-2">
          <Link
            to="/me"
            className="inline-flex h-11 items-center justify-center rounded-lg border border-[#e0e0e0] bg-white px-5 text-sm font-bold text-[#616161] transition-colors hover:bg-[#f5f5f5]"
          >
            취소
          </Link>
          <button
            type="submit"
            disabled={saving}
            className="inline-flex h-11 items-center justify-center gap-2 rounded-lg bg-[#d84315] px-5 text-sm font-bold text-white shadow-sm transition-colors hover:bg-[#bf360c] disabled:cursor-not-allowed disabled:bg-[#ffab91]"
          >
            {saving ? <Loader2 className="animate-spin" size={17} /> : <Save size={17} />}
            저장하기
          </button>
        </div>
      </form>
    </div>
  );
}

function FormField({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="mb-2 block text-sm font-bold text-[#616161]">{label}</span>
      {children}
    </label>
  );
}
