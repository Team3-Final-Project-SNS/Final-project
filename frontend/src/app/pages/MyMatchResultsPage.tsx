import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { AlertCircle, ArrowLeft, Check, Clock, MapPin, MessageCircle, Star, User } from 'lucide-react';
import { getMyMatches, GetMatchesItemResponse, MatchStatus } from '../../api/matchApi';
import { getUserMe } from '../../api/userApi';
import {
  createReview,
  getMyWrittenReviews,
  ReviewBadTag,
  ReviewGoodTag,
  ReviewItem,
} from '../../api/reviewApi';

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

export default function MyMatchResultsPage() {
  const [matches, setMatches] = useState<GetMatchesItemResponse[]>([]);
  const [writtenReviews, setWrittenReviews] = useState<Record<number, WrittenReview>>({});
  const [currentUserId, setCurrentUserId] = useState<number | null>(null);
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
        const status = activeFilter === '전체' ? undefined : activeFilter;
        const [matchRes, userRes] = await Promise.all([
          getMyMatches(status, page, 10),
          currentUserId === null ? getUserMe() : Promise.resolve(null),
        ]);
        const nextMatches = matchRes.data.data.content;
        const userId = currentUserId ?? userRes!.data.data.userId;

        if (currentUserId === null) {
          setCurrentUserId(userId);
        }

        setMatches(nextMatches);
        setTotalPages(matchRes.data.data.totalPages || 1);

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
      } catch (err) {
        console.error('Failed to load my match results', err);
        setError('매칭 결과를 불러오는데 실패했습니다.');
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
    if (!reviewTarget || currentUserId === null) {
      return;
    }

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
                  <MatchResultCard
                      key={match.matchId}
                      match={match}
                      writtenReview={writtenReviews[match.matchId]}
                      onWriteReview={() => setReviewTarget(match)}
                      onViewReview={() => setReviewViewer(writtenReviews[match.matchId])}
                  />
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

function MatchResultCard({
  match,
  writtenReview,
  onWriteReview,
  onViewReview,
}: {
  match: GetMatchesItemResponse;
  writtenReview?: WrittenReview;
  onWriteReview: () => void;
  onViewReview: () => void;
}) {
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
            {match.opponentNickname}
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
          {match.status === 'COMPLETED' && (
              writtenReview ? (
                  <button
                      type="button"
                      onClick={onViewReview}
                      className="inline-flex items-center justify-center gap-2 rounded-lg bg-[#2e7d32] px-4 py-2.5 text-sm font-semibold text-white shadow-md transition-colors hover:bg-[#1b5e20]"
                  >
                    <Star size={16} />
                    내가 작성한 리뷰 보기
                  </button>
              ) : (
                  <button
                      type="button"
                      onClick={onWriteReview}
                      className="inline-flex items-center justify-center gap-2 rounded-lg bg-[#d84315] px-4 py-2.5 text-sm font-semibold text-white shadow-md transition-colors hover:bg-[#bf360c]"
                  >
                    <Star size={16} />
                    리뷰 작성하기
                  </button>
              )
          )}
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
                className="flex flex-1 items-center justify-center gap-2 rounded-lg bg-[#d84315] py-3 font-semibold text-white hover:bg-[#bf360c] disabled:opacity-60"
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
            <p className="mt-1 text-xs text-[#9e9e9e]">{formatDateTime(review.createdAt)} 작성</p>
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

function formatDateTime(value: string) {
  return new Date(value).toLocaleString('ko-KR', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}
