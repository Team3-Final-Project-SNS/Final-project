import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { AlertCircle, ArrowLeft, Clock, MapPin, MessageCircle, User } from 'lucide-react';
import { getMyMatches, GetMatchesItemResponse, MatchStatus } from '../../api/matchApi';

type FilterStatus = '전체' | MatchStatus;

const filters: FilterStatus[] = [
  '전체',
  'MATCHED',
  'COMPLETED',
  'CANCELLED',
  'AUTHOR_NO_SHOW',
  'APPLICANT_NO_SHOW',
  'BOTH_NO_SHOW',
  'DISPUTED',
];

const statusLabels: Record<MatchStatus, string> = {
  MATCHED: '진행 중',
  COMPLETED: '만남 완료',
  CANCELLED: '취소됨',
  AUTHOR_NO_SHOW: '등록자 노쇼',
  APPLICANT_NO_SHOW: '신청자 노쇼',
  BOTH_NO_SHOW: '양측 노쇼',
  DISPUTED: '이의제기',
};

const statusClasses: Record<MatchStatus, string> = {
  MATCHED: 'bg-[#fff3e0] text-[#ef6c00]',
  COMPLETED: 'bg-[#e8f5e9] text-[#2e7d32]',
  CANCELLED: 'bg-[#f5f5f5] text-[#757575]',
  AUTHOR_NO_SHOW: 'bg-[#ffebee] text-[#c62828]',
  APPLICANT_NO_SHOW: 'bg-[#ffebee] text-[#c62828]',
  BOTH_NO_SHOW: 'bg-[#ffebee] text-[#c62828]',
  DISPUTED: 'bg-[#fce4ec] text-[#ad1457]',
};

export default function MyMatchResultsPage() {
  const [matches, setMatches] = useState<GetMatchesItemResponse[]>([]);
  const [activeFilter, setActiveFilter] = useState<FilterStatus>('전체');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);

  useEffect(() => {
    const fetchMatches = async () => {
      setLoading(true);
      setError('');
      try {
        const status = activeFilter === '전체' ? undefined : activeFilter;
        const res = await getMyMatches(status, page, 10);
        setMatches(res.data.data.content);
        setTotalPages(res.data.data.totalPages || 1);
      } catch (err) {
        console.error('Failed to load my match results', err);
        setError('매칭 결과를 불러오는데 실패했습니다.');
      } finally {
        setLoading(false);
      }
    };

    fetchMatches();
  }, [activeFilter, page]);

  return (
      <div className="mx-auto max-w-4xl">
        <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <Link
                to="/me"
                className="mb-3 inline-flex items-center gap-1 text-sm font-semibold text-[#616161] transition-colors hover:text-[#d84315]"
            >
              <ArrowLeft size={16} />
              내 정보
            </Link>
            <h1 className="text-3xl font-bold text-[#212121]">내 매칭 결과</h1>
            <p className="mt-2 text-sm text-[#757575]">참여했던 매칭과 채팅 내역을 확인할 수 있습니다.</p>
          </div>
          <Link
              to="/matches"
              className="inline-flex items-center justify-center rounded-lg bg-[#d84315] px-5 py-2.5 text-sm font-semibold text-white shadow-md transition-colors hover:bg-[#bf360c]"
          >
            진행 중 매칭 보기
          </Link>
        </div>

        <div className="mb-5 flex gap-2 overflow-x-auto pb-2">
          {filters.map((filter) => (
              <button
                  key={filter}
                  type="button"
                  onClick={() => {
                    setActiveFilter(filter);
                    setPage(0);
                  }}
                  className={`shrink-0 rounded-full px-4 py-2 text-sm font-semibold transition-colors ${
                      activeFilter === filter
                          ? 'bg-[#d84315] text-white'
                          : 'border border-[#e0e0e0] bg-white text-[#616161] hover:border-[#d84315]'
                  }`}
              >
                {filter === '전체' ? '전체' : statusLabels[filter]}
              </button>
          ))}
        </div>

        {error && (
            <div className="mb-5 flex items-start gap-2 rounded-lg border border-[#ef5350] bg-[#ffebee] px-4 py-3 text-sm text-[#c62828]">
              <AlertCircle size={18} className="mt-0.5 shrink-0" />
              <span>{error}</span>
            </div>
        )}

        {loading ? (
            <div className="space-y-3">
              {[1, 2, 3].map((item) => (
                  <div key={item} className="h-40 animate-pulse rounded-2xl border border-[#e0e0e0] bg-white p-5">
                    <div className="mb-4 h-5 w-20 rounded bg-gray-200" />
                    <div className="mb-3 h-6 w-1/2 rounded bg-gray-200" />
                    <div className="h-4 w-2/3 rounded bg-gray-200" />
                  </div>
              ))}
            </div>
        ) : matches.length > 0 ? (
            <div className="space-y-3">
              {matches.map((match) => (
                  <MatchResultCard key={match.matchId} match={match} />
              ))}
            </div>
        ) : (
            <div className="rounded-2xl border border-[#e0e0e0] bg-white p-12 text-center">
              <p className="text-sm font-semibold text-[#9e9e9e]">표시할 매칭 결과가 없습니다.</p>
              <Link to="/posts" className="mt-3 inline-block text-sm font-semibold text-[#d84315]">
                밥 친구 찾으러 가기
              </Link>
            </div>
        )}

        {totalPages > 1 && (
            <div className="mt-8 flex items-center justify-center gap-2">
              <button
                  type="button"
                  disabled={page === 0}
                  onClick={() => setPage((prev) => prev - 1)}
                  className="rounded border border-[#e0e0e0] px-3 py-1.5 text-sm hover:bg-[#f5f5f5] disabled:opacity-50"
              >
                &lt;
              </button>
              {[...Array(totalPages)].map((_, index) => (
                  <button
                      key={index}
                      type="button"
                      onClick={() => setPage(index)}
                      className={`rounded px-3 py-1.5 text-sm ${
                          page === index ? 'bg-[#d84315] text-white' : 'border border-[#e0e0e0] hover:bg-[#f5f5f5]'
                      }`}
                  >
                    {index + 1}
                  </button>
              ))}
              <button
                  type="button"
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage((prev) => prev + 1)}
                  className="rounded border border-[#e0e0e0] px-3 py-1.5 text-sm hover:bg-[#f5f5f5] disabled:opacity-50"
              >
                &gt;
              </button>
            </div>
        )}
      </div>
  );
}

function MatchResultCard({ match }: { match: GetMatchesItemResponse }) {
  return (
      <div className="rounded-2xl border border-[#e0e0e0] bg-white p-5 shadow-sm transition-all hover:border-[#d84315] hover:shadow-md">
        <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
          <div className="flex items-center gap-2">
            <span className={`rounded px-2.5 py-1 text-xs font-bold ${statusClasses[match.status]}`}>
              {statusLabels[match.status]}
            </span>
            <span className="text-xs font-semibold text-[#9e9e9e]">{formatDateTime(match.matchedAt)} 매칭</span>
          </div>
          <span className="text-sm font-bold text-[#d84315]">{match.myDeposit.toLocaleString()}P</span>
        </div>

        <h2 className="mb-3 text-lg font-bold text-[#212121]">{match.placeName} 만남</h2>

        <div className="mb-5 grid gap-2 text-sm text-[#616161] sm:grid-cols-2">
          <span className="flex items-center gap-1.5">
            <User size={16} className="text-[#d84315]" />
            {match.opponentNickname} ({match.opponentMajor} {match.opponentStudentNumber}학번)
          </span>
          <span className="flex items-center gap-1.5">
            <Clock size={16} className="text-[#d84315]" />
            {formatDateTime(match.meetAt)}
          </span>
          <span className="flex items-center gap-1.5 sm:col-span-2">
            <MapPin size={16} className="text-[#d84315]" />
            {match.placeName}
          </span>
        </div>

        <div className="flex flex-wrap justify-end gap-2">
          <Link
              to={`/chat/${match.chatRoomId}`}
              state={{ matchId: match.matchId }}
              className="inline-flex items-center justify-center gap-2 rounded-lg border border-[#e0e0e0] bg-white px-4 py-2.5 text-sm font-semibold text-[#616161] transition-colors hover:border-[#d84315] hover:text-[#d84315]"
          >
            <MessageCircle size={16} />
            채팅 보기
          </Link>
          {match.status === 'MATCHED' && (
              <Link
                  to={`/matches/${match.matchId}/place-verification`}
                  className="inline-flex items-center justify-center rounded-lg bg-[#d84315] px-4 py-2.5 text-sm font-semibold text-white shadow-md transition-colors hover:bg-[#bf360c]"
              >
                인증 진행
              </Link>
          )}
        </div>
      </div>
  );
}

function formatDateTime(value: string) {
  return new Date(value).toLocaleString('ko-KR', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}
