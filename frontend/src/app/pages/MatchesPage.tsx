import { useState, useEffect } from 'react';
import { Link } from 'react-router';
import { MapPin, Clock, MessageCircle, AlertCircle, Loader2, XCircle, Star, Check } from 'lucide-react';
import { getMyMatches, GetMatchesItemResponse, MatchStatus, updateMatchCancel } from '../../api/matchApi';
import { getUserMe } from '../../api/userApi';
import {
  createReview,
  getMyWrittenReviews,
  ReviewBadTag,
  ReviewGoodTag,
  ReviewItem,
} from '../../api/reviewApi';

type FilterStatus = MatchStatus | '전체';

const goodTagOptions: { value: ReviewGoodTag; label: string }[] = [
  { value: 'ON_TIME', label: '시간 약속을 잘 지켜요' },
  { value: 'KIND', label: '친절해요' },
  { value: 'GOOD_COMMUNICATION', label: '대화가 잘 통해요' },
  { value: 'CLEAN_MANNER', label: '식사 매너가 좋아요' },
  { value: 'WANT_MEET_AGAIN', label: '다시 만나고 싶어요' },
];

const badTagOptions: { value: ReviewBadTag; label: string }[] = [
  { value: 'LATE', label: '약속 시간에 늦었어요' },
  { value: 'NO_REPLY', label: '답장이 잘 오지 않았어요' },
  { value: 'UNCOMFORTABLE', label: '대화가 불편했어요' },
  { value: 'BAD_MANNER', label: '식사 매너가 아쉬웠어요' },
  { value: 'REPORT_NEEDED', label: '신고가 필요해요' },
];

type WrittenReview = ReviewItem | {
  reviewId: number;
  matchId: number;
  writerId: number;
  writerNickname: string;
  goodTags: ReviewGoodTag[];
  badTags: ReviewBadTag[];
  tagScoreDelta: number;
  reportNeeded: boolean;
  createdAt: string;
};

export default function MatchesPage() {
  const [matches, setMatches] = useState<GetMatchesItemResponse[]>([]);
  const [currentUserId, setCurrentUserId] = useState<number | null>(null);
  const [writtenReviews, setWrittenReviews] = useState<Record<number, WrittenReview>>({});
  const [activeFilter, setActiveFilter] = useState<FilterStatus>('전체');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [reviewTarget, setReviewTarget] = useState<GetMatchesItemResponse | null>(null);
  const [reviewViewer, setReviewViewer] = useState<WrittenReview | null>(null);
  const [selectedGoodTags, setSelectedGoodTags] = useState<ReviewGoodTag[]>([]);
  const [selectedBadTags, setSelectedBadTags] = useState<ReviewBadTag[]>([]);
  const [reviewSubmitting, setReviewSubmitting] = useState(false);

  useEffect(() => {
    const fetchMatches = async () => {
      setLoading(true);
      setError('');
      try {
        const statusParam = activeFilter === '전체' ? undefined : activeFilter as MatchStatus;
        const [matchRes, userRes] = await Promise.all([
          getMyMatches(statusParam, page, 10),
          currentUserId === null ? getUserMe() : Promise.resolve(null),
        ]);
        const nextMatches = matchRes.data.data.content;
        const userId = currentUserId ?? userRes!.data.data.userId;

        if (currentUserId === null) {
          setCurrentUserId(userId);
        }

        // backend PageResponseDto has 'content', 'page', 'size', 'totalPages', 'hasNext'
        setMatches(nextMatches);
        setTotalPages(matchRes.data.data.totalPages);

        const completedMatches = nextMatches.filter((match) => match.status === 'COMPLETED');
        if (completedMatches.length > 0) {
          const completedMatchIds = new Set(completedMatches.map((match) => match.matchId));
          const reviewRes = await getMyWrittenReviews(0, 50);
          const reviewEntries = reviewRes.data.data.content
            .filter((review) => completedMatchIds.has(review.matchId) && review.writerId === userId)
            .map((review) => [review.matchId, review] as const);

          setWrittenReviews((prev) => ({
            ...prev,
            ...Object.fromEntries(reviewEntries),
          }));
        }
      } catch (err: any) {
        setError('매칭 내역을 불러오는데 실패했습니다.');
        console.error(err);
      } finally {
        setLoading(false);
      }
    };
    fetchMatches();
  }, [activeFilter, page, currentUserId]);

  const toggleGoodTag = (tag: ReviewGoodTag) => {
    setSelectedBadTags([]);
    setSelectedGoodTags((prev) =>
        prev.includes(tag) ? prev.filter((item) => item !== tag) : [...prev, tag],
    );
  };

  const toggleBadTag = (tag: ReviewBadTag) => {
    setSelectedGoodTags([]);
    setSelectedBadTags((prev) =>
        prev.includes(tag) ? prev.filter((item) => item !== tag) : [...prev, tag],
    );
  };

  const handleCreateReview = async () => {
    if (!reviewTarget || currentUserId === null) return;

    if (selectedGoodTags.length === 0 && selectedBadTags.length === 0) {
      setError('리뷰 태그를 하나 이상 선택해주세요.');
      return;
    }

    setReviewSubmitting(true);
    setError('');
    try {
      const res = await createReview(reviewTarget.matchId, {
        goodTags: selectedGoodTags,
        badTags: selectedBadTags,
      });
      const created = res.data.data;
      setWrittenReviews((prev) => ({
        ...prev,
        [reviewTarget.matchId]: {
          reviewId: created.reviewId,
          matchId: created.matchId,
          writerId: currentUserId,
          writerNickname: '나',
          goodTags: created.goodTags,
          badTags: created.badTags,
          tagScoreDelta: created.tagScoreDelta,
          reportNeeded: created.reportNeeded,
          createdAt: created.createdAt,
        },
      }));
      setReviewTarget(null);
      setSelectedGoodTags([]);
      setSelectedBadTags([]);
    } catch (err: any) {
      setError(err.response?.data?.message || '리뷰 작성에 실패했습니다.');
    } finally {
      setReviewSubmitting(false);
    }
  };

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

  const handleCancelMatch = async (matchId: number) => {
    const ok = window.confirm('매칭을 취소하시겠습니까? 취소 시 책임비 일부가 차감될 수 있습니다.');
    if (!ok) return;

    try {
      await updateMatchCancel(matchId, '사용자 요청');
      setMatches((prev) =>
          prev.map((match) => match.matchId === matchId ? { ...match, status: 'CANCELLED' } : match)
      );
      alert('매칭이 취소되었습니다.');
    } catch (err: any) {
      alert(err.response?.data?.message || '매칭 취소에 실패했습니다.');
    }
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
                              <span className="font-medium text-[#424242]">상대방:</span> {match.opponentNickname}
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
                                      to={`/matches/${match.matchId}/place-verification`}
                                      className="flex-1 flex items-center justify-center gap-2 py-2.5 bg-[#d84315] text-white rounded-lg text-sm font-semibold hover:bg-[#bf360c] transition-colors"
                                  >
                                    <MapPin size={16} />
                                    인증/지도
                                  </Link>
                                  <Link
                                      to={`/chat/${match.chatRoomId}`}
                                      state={{ matchId: match.matchId }}
                                      className="flex-1 flex items-center justify-center gap-2 py-2.5 bg-white border border-[#e0e0e0] rounded-lg text-sm font-semibold text-[#616161] hover:bg-[#f5f5f5] transition-colors"
                                  >
                                    <MessageCircle size={16} />
                                    채팅
                                  </Link>
                                  <button
                                      type="button"
                                      onClick={() => handleCancelMatch(match.matchId)}
                                      className="flex-1 flex items-center justify-center gap-2 py-2.5 bg-white border border-[#ef5350] rounded-lg text-sm font-semibold text-[#c62828] hover:bg-[#ffebee] transition-colors"
                                  >
                                    <XCircle size={16} />
                                    매칭 취소
                                  </button>
                                </>
                            )}
                            {match.status === 'COMPLETED' && (
                                <>
                                  <Link
                                      to={`/chat/${match.chatRoomId}`}
                                      state={{ matchId: match.matchId }}
                                      className="flex-1 flex items-center justify-center gap-2 py-2.5 bg-white border border-[#e0e0e0] rounded-lg text-sm font-semibold text-[#616161] hover:bg-[#f5f5f5] transition-colors"
                                  >
                                    <MessageCircle size={16} />
                                    채팅
                                  </Link>
                                  {writtenReviews[match.matchId] ? (
                                      <button
                                          type="button"
                                          onClick={() => setReviewViewer(writtenReviews[match.matchId])}
                                          className="flex-1 flex items-center justify-center gap-2 py-2.5 bg-[#2e7d32] text-white rounded-lg text-sm font-semibold hover:bg-[#1b5e20] transition-colors"
                                      >
                                        <Star size={16} />
                                        내가 작성한 리뷰 보기
                                      </button>
                                  ) : (
                                      <button
                                          type="button"
                                          onClick={() => setReviewTarget(match)}
                                          className="flex-1 flex items-center justify-center gap-2 py-2.5 bg-[#d84315] text-white rounded-lg text-sm font-semibold hover:bg-[#bf360c] transition-colors"
                                      >
                                        <Star size={16} />
                                        리뷰 작성하기
                                      </button>
                                  )}
                                </>
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

        {reviewTarget && (
            <ReviewWriteModal
                match={reviewTarget}
                selectedGoodTags={selectedGoodTags}
                selectedBadTags={selectedBadTags}
                submitting={reviewSubmitting}
                onToggleGoodTag={toggleGoodTag}
                onToggleBadTag={toggleBadTag}
                onClose={() => {
                  setReviewTarget(null);
                  setSelectedGoodTags([]);
                  setSelectedBadTags([]);
                }}
                onSubmit={handleCreateReview}
            />
        )}

        {reviewViewer && (
            <ReviewViewModal review={reviewViewer} onClose={() => setReviewViewer(null)} />
        )}
      </div>
  );
}

function ReviewWriteModal({
  match,
  selectedGoodTags,
  selectedBadTags,
  submitting,
  onToggleGoodTag,
  onToggleBadTag,
  onClose,
  onSubmit,
}: {
  match: GetMatchesItemResponse;
  selectedGoodTags: ReviewGoodTag[];
  selectedBadTags: ReviewBadTag[];
  submitting: boolean;
  onToggleGoodTag: (tag: ReviewGoodTag) => void;
  onToggleBadTag: (tag: ReviewBadTag) => void;
  onClose: () => void;
  onSubmit: () => void;
}) {
  return (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-50 p-4">
        <div className="w-full max-w-lg rounded-2xl bg-white p-6 shadow-xl">
          <h2 className="text-xl font-bold text-[#212121]">리뷰 작성하기</h2>
          <p className="mt-1 text-sm text-[#757575]">{match.opponentNickname}님과의 만남 후기를 남겨주세요.</p>

          <div className="mt-5">
            <p className="mb-2 text-sm font-bold text-[#212121]">좋았던 점</p>
            <div className="flex flex-wrap gap-2">
              {goodTagOptions.map((option) => (
                  <TagButton
                      key={option.value}
                      selected={selectedGoodTags.includes(option.value)}
                      label={option.label}
                      onClick={() => onToggleGoodTag(option.value)}
                  />
              ))}
            </div>
          </div>

          <div className="mt-5">
            <p className="mb-2 text-sm font-bold text-[#212121]">아쉬웠던 점</p>
            <div className="flex flex-wrap gap-2">
              {badTagOptions.map((option) => (
                  <TagButton
                      key={option.value}
                      selected={selectedBadTags.includes(option.value)}
                      label={option.label}
                      onClick={() => onToggleBadTag(option.value)}
                  />
              ))}
            </div>
          </div>

          <p className="mt-4 text-xs text-[#9e9e9e]">좋았던 점과 아쉬웠던 점은 동시에 선택할 수 없습니다.</p>

          <div className="mt-6 flex gap-3">
            <button
                type="button"
                onClick={onClose}
                disabled={submitting}
                className="flex-1 rounded-lg border border-[#e0e0e0] py-3 font-semibold text-[#616161] hover:bg-[#f5f5f5]"
            >
              취소
            </button>
            <button
                type="button"
                onClick={onSubmit}
                disabled={submitting}
                className="flex flex-1 items-center justify-center rounded-lg bg-[#d84315] py-3 font-semibold text-white hover:bg-[#bf360c] disabled:opacity-60"
            >
              {submitting ? '작성 중...' : '리뷰 등록'}
            </button>
          </div>
        </div>
      </div>
  );
}

function ReviewViewModal({ review, onClose }: { review: WrittenReview; onClose: () => void }) {
  const tags = [
    ...review.goodTags.map((tag) => goodTagOptions.find((option) => option.value === tag)?.label || tag),
    ...review.badTags.map((tag) => badTagOptions.find((option) => option.value === tag)?.label || tag),
  ];

  return (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-50 p-4">
        <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl">
          <div className="mb-4 flex items-center gap-2">
            <Check className="text-[#2e7d32]" size={22} />
            <h2 className="text-xl font-bold text-[#212121]">내가 작성한 리뷰</h2>
          </div>
          <div className="rounded-xl bg-[#fafafa] p-4">
            <p className="mb-3 text-sm font-bold text-[#616161]">선택한 태그</p>
            <div className="flex flex-wrap gap-2">
              {tags.map((tag) => (
                  <span key={tag} className="rounded-full bg-white px-3 py-1.5 text-sm font-semibold text-[#424242]">
                    {tag}
                  </span>
              ))}
            </div>
            <p className="mt-4 text-sm text-[#757575]">매너 온도 반영 점수: {review.tagScoreDelta > 0 ? '+' : ''}{review.tagScoreDelta}</p>
            <p className="mt-1 text-xs text-[#9e9e9e]">{new Date(review.createdAt).toLocaleString('ko-KR')} 작성</p>
          </div>
          <button
              type="button"
              onClick={onClose}
              className="mt-5 w-full rounded-lg bg-[#d84315] py-3 font-semibold text-white hover:bg-[#bf360c]"
          >
            확인
          </button>
        </div>
      </div>
  );
}

function TagButton({ selected, label, onClick }: { selected: boolean; label: string; onClick: () => void }) {
  return (
      <button
          type="button"
          onClick={onClick}
          className={`rounded-full border px-3 py-2 text-sm font-semibold transition-colors ${
              selected
                  ? 'border-[#d84315] bg-[#fff3e0] text-[#d84315]'
                  : 'border-[#e0e0e0] bg-white text-[#616161] hover:border-[#d84315]'
          }`}
      >
        {label}
      </button>
  );
}
