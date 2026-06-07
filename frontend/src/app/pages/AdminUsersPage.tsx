import { useEffect, useMemo, useState } from 'react';
import { Loader2, Search, Users } from 'lucide-react';
import { AdminUserItem, AdminUserStatus, getAdminUsers } from '../../api/adminUserApi';
import { getUniversities, UniversityResponse } from '../../api/univApi';

const statusLabels: Record<AdminUserStatus, string> = {
  ACTIVE: '활성',
  SUSPENDED: '정지',
  WITHDRAWN: '탈퇴',
};

const statusClasses: Record<AdminUserStatus, string> = {
  ACTIVE: 'bg-[#e8f5e9] text-[#2e7d32]',
  SUSPENDED: 'bg-[#fff3e0] text-[#ef6c00]',
  WITHDRAWN: 'bg-[#ffebee] text-[#c62828]',
};

export default function AdminUsersPage() {
  const [users, setUsers] = useState<AdminUserItem[]>([]);
  const [universities, setUniversities] = useState<UniversityResponse[]>([]);
  const [universityName, setUniversityName] = useState('ALL');
  const [status, setStatus] = useState<'ALL' | AdminUserStatus>('ALL');
  const [searchStatus, setSearchStatus] = useState<'ALL' | AdminUserStatus>('ALL');
  const [keyword, setKeyword] = useState('');
  const [searchKeyword, setSearchKeyword] = useState('');
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState('');

  useEffect(() => {
    const loadUniversities = async () => {
      try {
        const universityRes = await getUniversities();
        setUniversities(universityRes.data.data);
      } catch (err) {
        console.error('학교 목록 조회 실패', err);
      }
    };

    loadUniversities();
  }, []);

  useEffect(() => {
    const loadUsers = async () => {
      setLoading(true);
      setMessage('');
      try {
        // 관리자 유저 목록 API와 직접 연결합니다.
        // 학교별 필터는 현재 백엔드 파라미터가 없어서 응답 데이터 기준으로 화면에서 필터링합니다.
        const userRes = await getAdminUsers(
          searchStatus === 'ALL' ? undefined : searchStatus,
          searchKeyword.trim() || undefined,
          0,
          100,
        );
        setUsers(userRes.data.data.content);
      } catch (err: any) {
        const statusCode = err.response?.status;
        const serverMessage = err.response?.data?.message;
        setMessage(serverMessage ? `유저 목록 조회 실패 (${statusCode}): ${serverMessage}` : '유저 목록을 불러오지 못했습니다.');
      } finally {
        setLoading(false);
      }
    };

    loadUsers();
  }, [searchStatus, searchKeyword]);

  const handleSearch = () => {
    // 조회 버튼을 누른 시점의 상태/검색어를 실제 API 조회 조건으로 반영합니다.
    setSearchStatus(status);
    setSearchKeyword(keyword);
  };

  const filteredUsers = useMemo(() => {
    return users.filter((user) => {
      const matchesUniversity = universityName === 'ALL' || user.universityName === universityName;

      return matchesUniversity;
    });
  }, [users, universityName]);

  return (
    <AdminPageShell title="유저 목록" description="학교별 유저 상태와 포인트, 매너온도를 확인합니다.">
      <div className="mb-5 grid gap-3 rounded-2xl border border-[#e0e0e0] bg-white p-4 md:grid-cols-[1fr_1fr_2fr_auto]">
        <select
          value={universityName}
          onChange={(event) => setUniversityName(event.target.value)}
          className="h-11 rounded-lg border border-[#e0e0e0] bg-white px-3 text-sm font-semibold text-[#424242] outline-none focus:border-[#d84315]"
        >
          <option value="ALL">전체 학교</option>
          {universities.map((university) => (
            <option key={university.universityId} value={university.universityName}>
              {university.universityName}
            </option>
          ))}
        </select>

        <select
          value={status}
          onChange={(event) => setStatus(event.target.value as 'ALL' | AdminUserStatus)}
          className="h-11 rounded-lg border border-[#e0e0e0] bg-white px-3 text-sm font-semibold text-[#424242] outline-none focus:border-[#d84315]"
        >
          <option value="ALL">전체 상태</option>
          <option value="ACTIVE">활성</option>
          <option value="SUSPENDED">정지</option>
          <option value="WITHDRAWN">탈퇴</option>
        </select>

        <label className="flex h-11 items-center gap-2 rounded-lg border border-[#e0e0e0] bg-white px-3">
          <Search size={16} className="text-[#9e9e9e]" />
          <input
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter') {
                handleSearch();
              }
            }}
            placeholder="이메일, 이름, 닉네임 검색"
            className="w-full text-sm outline-none"
          />
        </label>

        <button
          type="button"
          onClick={handleSearch}
          className="h-11 rounded-lg bg-[#d84315] px-5 text-sm font-bold text-white shadow-sm transition-colors hover:bg-[#bf360c]"
        >
          조회
        </button>
      </div>

      {message && (
        <div className="mb-5 rounded-lg border border-[#ef5350] bg-[#ffebee] px-4 py-3 text-sm font-semibold text-[#c62828]">
          {message}
        </div>
      )}

      {loading ? (
        <div className="rounded-2xl border border-[#e0e0e0] bg-white p-12 text-center text-[#9e9e9e]">
          <Loader2 className="mx-auto mb-3 animate-spin text-[#d84315]" />
          유저 목록을 불러오는 중...
        </div>
      ) : (
        <div className="overflow-hidden rounded-2xl border border-[#e0e0e0] bg-white shadow-sm">
          <div className="grid grid-cols-[1.2fr_1fr_1fr_0.8fr_0.8fr_0.8fr] gap-3 border-b border-[#eeeeee] bg-[#fafafa] px-5 py-3 text-xs font-bold text-[#757575]">
            <span>유저</span>
            <span>학교</span>
            <span>닉네임</span>
            <span>포인트</span>
            <span>매너온도</span>
            <span>상태</span>
          </div>

          {filteredUsers.length > 0 ? (
            filteredUsers.map((user) => (
              <div
                key={user.userId}
                className="grid grid-cols-[1.2fr_1fr_1fr_0.8fr_0.8fr_0.8fr] gap-3 border-b border-[#f5f5f5] px-5 py-4 text-sm last:border-b-0"
              >
                <div>
                  <p className="font-bold text-[#212121]">{user.name}</p>
                  <p className="mt-1 text-xs text-[#757575]">{user.email}</p>
                </div>
                <span className="font-semibold text-[#424242]">{user.universityName}</span>
                <span className="text-[#616161]">{user.nickname}</span>
                <span className="font-bold text-[#d84315]">{user.point.toLocaleString()}P</span>
                <span className="font-bold text-[#2e7d32]">{Number(user.mannerTemperature).toFixed(1)}°C</span>
                <span>
                  <span className={`rounded px-2.5 py-1 text-xs font-bold ${statusClasses[user.status]}`}>
                    {statusLabels[user.status]}
                  </span>
                </span>
              </div>
            ))
          ) : (
            <div className="p-12 text-center text-sm font-semibold text-[#9e9e9e]">
              조건에 맞는 유저가 없습니다.
            </div>
          )}
        </div>
      )}
    </AdminPageShell>
  );
}

function AdminPageShell({ title, description, children }: { title: string; description: string; children: React.ReactNode }) {
  return (
    <div className="min-h-screen bg-gradient-to-br from-[#fff7ed] via-[#f7fbff] to-[#eaf7f1]">
      <main className="mx-auto max-w-screen-lg px-4 py-10">
        <div className="mb-6 flex items-center gap-3">
          <Users className="text-[#d84315]" size={28} />
          <div>
            <h1 className="text-3xl font-bold text-[#212121]">{title}</h1>
            <p className="mt-1 text-sm text-[#757575]">{description}</p>
          </div>
        </div>
        {children}
      </main>
    </div>
  );
}
