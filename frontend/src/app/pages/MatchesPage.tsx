import { useState, useEffect } from 'react';
import { Link } from 'react-router';
import { MapPin, Clock, MessageCircle, QrCode, CheckCircle, AlertCircle, Loader2 } from 'lucide-react';
import { getMyMatches, GetMatchesItemResponse, MatchStatus } from '../../api/matchApi';

type FilterStatus = MatchStatus | '전체';

export default function MatchesPage() {
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
        const statusParam = activeFilter === '전체' ? undefined : activeFilter as MatchStatus;
        const res = await getMyMatches(statusParam, page, 10);
        // backend PageResponseDto has 'content', 'page', 'size', 'totalPages', 'hasNext'
        setMatches(res.data.data.content);
        setTotalPages(res.data.data.totalPages);
      } catch (err: any) {
        setError('매칭 내역을 불러오는데 실패했습니다.');
        console.error(err);
      } finally {
        setLoading(false);
      }
    };
    fetchMatches();
  }, [activeFilter, page]);

  const getStatusBadge = (status: MatchStatus) => {
    switch (status) {
      case 'MATCHED':
        return { text: '매칭됨', color: 'bg-[#ff9800] text-white' };
      case 'DISPUTED':
        return { text: '이의제기', color: 'bg-[#f44336] text-white' };
      case 'COMPLETED':
        return { text: '만남 완료', color: 'bg-[#2196f3] text-white' };
      case 'CANCELLED':
        return { text: '취소됨', color: 'bg-[#9e9e9e] text-white' };
      default:
        return { text: status, color: 'bg-gray-200 text-gray-700' };
    }
  };

  const getTimeAgo = (dateStr: string) => {
    const date = new Date(dateStr);
    return date.toLocaleDateString() + ' ' + date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  };

  return (
      <div>
        <h1 className="text-3xl font-bold text-[#212121] mb-8">내 매칭</h1>

        <div className="flex gap-2 mb-6 overflow-x-auto pb-2">

          {['전체', 'MATCHED', 'COMPLETED', 'CANCELLED', 'AUTHOR_NO_SHOW', 'APPLICANT_NO_SHOW', 'BOTH_NO_SHOW', 'DISPUTED'].map((filter) => (
              <button
                  key={filter}
                  onClick={() => {
                      setActiveFilter(filter as FilterStatus);
                      setPage(0);
                  }}
                  className={`px-4 py-2 rounded-full text-sm font-medium transition-colors whitespace-nowrap ${
                      activeFilter === filter
                          ? 'bg-[#d84315] text-white'
                          : 'bg-white border border-[#e0e0e0] text-[#616161] hover:border-[#d84315]'
                  }`}
              >
                {filter === '전체' ? '전체' :
                    filter === 'MATCHED' ? '진행 중' :
                        filter === 'COMPLETED' ? '완료됨' :
                            filter === 'CANCELLED' ? '취소됨' :
                                filter === 'AUTHOR_NO_SHOW' ? '등록자 노쇼' :
                                    filter === 'APPLICANT_NO_SHOW' ? '신청자 노쇼' :
                                        filter === 'BOTH_NO_SHOW' ? '양측 노쇼' : '이의제기'}
              </button>
          ))}
        </div>

        {error && (
            <div className="mb-6 bg-[#ffebee] border border-[#ef5350] rounded-lg px-4 py-3 flex items-start gap-2">
              <AlertCircle size={18} className="text-[#c62828] mt-0.5" />
              <span className="text-[#c62828] text-sm">{error}</span>
            </div>
        )}

        {loading ? (
            <div className="space-y-4">
              {[1, 2, 3].map(n => (
                  <div key={n} className="bg-white border border-[#e0e0e0] rounded-xl p-6 h-40 animate-pulse">
                    <div className="h-4 bg-gray-200 rounded w-20 mb-4"></div>
                    <div className="h-6 bg-gray-200 rounded w-1/2 mb-4"></div>
                    <div className="h-4 bg-gray-200 rounded w-1/4"></div>
                  </div>
              ))}
            </div>
        ) : (
            <div className="space-y-4">
              {matches.length === 0 ? (
                  <div className="text-center py-20 bg-white border border-[#e0e0e0] rounded-xl">
                      <p className="text-[#9e9e9e]">매칭 내역이 없습니다.</p>
                      <Link to="/posts" className="text-[#d84315] font-medium mt-2 inline-block">밥 친구 찾으러 가기</Link>
                  </div>
              ) : (
                  matches.map((match) => {
                    const badge = getStatusBadge(match.status);

                    return (
                        <div
                            key={match.matchId}
                            className="bg-white border border-[#e0e0e0] rounded-xl p-6 hover:shadow-lg transition-all hover:border-[#d84315]"
                        >
                          <div className="flex items-start justify-between mb-4">
                        <span className={`px-3 py-1 rounded text-xs font-semibold ${badge.color}`}>
                          {badge.text}
                        </span>
                            <span className="text-sm text-[#9e9e9e]">{getTimeAgo(match.matchedAt)}</span>
                          </div>

                          <h3 className="font-semibold text-[#212121] mb-3">{match.placeName} 만남</h3>

                          <div className="space-y-2 mb-4">
                            <div className="flex items-center gap-2 text-sm text-[#616161]">
                              <span className="font-medium text-[#424242]">상대방:</span> {match.opponentNickname} ({match.opponentMajor} {match.opponentStudentNumber}학번)
                            </div>
                            <div className="flex items-center gap-2 text-sm text-[#616161]">
                              <Clock size={16} className="text-[#d84315]" />
                              <span>{new Date(match.meetAt).toLocaleString()}</span>
                            </div>
                            <div className="flex items-center gap-2 text-sm text-[#616161]">
                              <span className="font-medium text-[#424242]">내 책임비:</span> {match.myDeposit.toLocaleString()}P
                            </div>
                          </div>

                          <div className="flex gap-2">
                            {(match.status === 'MATCHED' || match.status === 'DISPUTED') && (
                                <>
                                  <Link
                                      to={`/chat/${match.chatRoomId}`}
                                      state={{ matchId: match.matchId }}
                                      className="flex-1 flex items-center justify-center gap-2 py-2.5 bg-white border border-[#e0e0e0] rounded-lg text-sm font-semibold text-[#616161] hover:bg-[#f5f5f5] transition-colors"
                                  >
                                    <MessageCircle size={16} />
                                    채팅
                                  </Link>
                                  <Link
                                      to={`/matches/${match.matchId}/place-verification`}
                                      className="flex-1 flex items-center justify-center gap-2 py-2.5 bg-[#d84315] text-white rounded-lg text-sm font-semibold hover:bg-[#bf360c] transition-colors"
                                  >
                                    <MapPin size={16} />
                                    인증/지도
                                  </Link>
                                </>
                            )}
                            {match.status === 'COMPLETED' && (
                                <div className="flex-1 text-center py-2.5 bg-[#e8f5e9] text-[#2e7d32] rounded-lg text-sm font-semibold">
                                  식사가 완료되었습니다.
                                </div>
                            )}
                            {match.status === 'CANCELLED' && (
                                <div className="flex-1 text-center py-2.5 bg-[#f5f5f5] text-[#9e9e9e] rounded-lg text-sm font-semibold">
                                  취소된 매칭입니다.
                                </div>
                            )}
                          </div>
                        </div>
                    );
                  })
              )}
            </div>
        )}

        {totalPages > 1 && (
            <div className="mt-8 flex items-center justify-center gap-2">
              <button 
                disabled={page === 0}
                onClick={() => setPage(p => p - 1)}
                className="px-3 py-1.5 border border-[#e0e0e0] rounded text-sm hover:bg-[#f5f5f5] disabled:opacity-50"
              >
                &lt;
              </button>
              {[...Array(totalPages)].map((_, i) => (
                <button
                  key={i}
                  onClick={() => setPage(i)}
                  className={`px-3 py-1.5 rounded text-sm ${
                    page === i ? 'bg-[#d84315] text-white' : 'border border-[#e0e0e0] hover:bg-[#f5f5f5]'
                  }`}
                >
                  {i + 1}
                </button>
              ))}
              <button 
                disabled={page >= totalPages - 1}
                onClick={() => setPage(p => p + 1)}
                className="px-3 py-1.5 border border-[#e0e0e0] rounded text-sm hover:bg-[#f5f5f5] disabled:opacity-50"
              >
                &gt;
              </button>
            </div>
        )}
      </div>
  );
}
