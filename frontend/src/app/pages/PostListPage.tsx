import { useEffect, useState } from 'react';
import { Link, useSearchParams, useNavigate } from 'react-router';
import { MapPin, Clock, Plus, AlertCircle, User, Users, Thermometer } from 'lucide-react';
import { getPosts, PostItemResponse, PostStatus } from '../../api/postApi';
import { getUserMe } from '../../api/userApi';
import { getMyMatches } from '../../api/matchApi';
import { clearAccessToken } from '../../api/axiosInstance';

const POST_STATUSES_FOR_MY_POSTS: PostStatus[] = ['OPEN', 'MATCHED', 'COMPLETED', 'CANCELLED', 'EXPIRED'];

type SortOption = '책임비 높은 순' | '만남시간 빠른 순' | '최신순';

const sortParams = {
  '책임비 높은 순': 'DEPOSIT_DESC',
  '만남시간 빠른 순': 'MEET_AT_ASC',
  '최신순': 'LATEST',
} as const;

const statusLabels: Record<PostStatus, string> = {
  OPEN: '모집중',
  MATCHED: '매칭됨',
  COMPLETED: '완료',
  CANCELLED: '취소',
  EXPIRED: '만료',
  DELETED: '삭제됨',
};

const statusClasses: Record<PostStatus, string> = {
  OPEN: 'bg-[#e8f5e9] text-[#2e7d32]',
  MATCHED: 'bg-[#fff3e0] text-[#ef6c00]',
  COMPLETED: 'bg-[#e3f2fd] text-[#1565c0]',
  CANCELLED: 'bg-[#ffebee] text-[#c62828]',
  EXPIRED: 'bg-[#ede7f6] text-[#5e35b1]',
  DELETED: 'bg-[#f5f5f5] text-[#757575]',
};

export default function PostListPage() {
  // [추가] 401 발생 시 메인화면("/")으로 이동시키기 위한 navigate
  const navigate = useNavigate();

  const [searchParams] = useSearchParams();
  const myPostsOnly = searchParams.get('mine') === '1';
  const [posts, setPosts] = useState<PostItemResponse[]>([]);
  const [sortBy, setSortBy] = useState<SortOption>('책임비 높은 순');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [currentUserId, setCurrentUserId] = useState<number | null>(null);
  const [activeMatchedPostIds, setActiveMatchedPostIds] = useState<Set<number>>(new Set());

  useEffect(() => {
    setPage(0);
  }, [myPostsOnly, sortBy]);

  useEffect(() => {
    const fetchPosts = async () => {
      setLoading(true);
      setError('');
      try {
        let userId = currentUserId;
        if (userId === null) {
          const userRes = await getUserMe();
          userId = userRes.data.data.userId;
          setCurrentUserId(userId);
        }

        const activeMatchPromise = getMyMatches('MATCHED', 0, 100);

        if (myPostsOnly) {
          const [statusResponses, activeMatchRes] = await Promise.all([
            Promise.all(POST_STATUSES_FOR_MY_POSTS.map((postStatus) => getPosts(postStatus, 0, 50, sortParams[sortBy]))),
            activeMatchPromise,
          ]);
          const mergedPosts = statusResponses
              .flatMap((response) => response.data.data.content)
              .filter((post) => post.authorId === userId)
              .filter((post, index, self) => self.findIndex((item) => item.postId === post.postId) === index);

          setActiveMatchedPostIds(new Set(activeMatchRes.data.data.content.map((match) => match.postId)));
          setPosts(mergedPosts);
          setTotalPages(1);
        } else {
          const [res, activeMatchRes] = await Promise.all([
            getPosts('OPEN', page, 20, sortParams[sortBy]),
            activeMatchPromise,
          ]);
          setActiveMatchedPostIds(new Set(activeMatchRes.data.data.content.map((match) => match.postId)));
          setPosts(res.data.data.content);
          setTotalPages(res.data.data.totalPages || 1);
        }
      } catch (err: any) {
        // [추가] 401 Unauthorized 처리
        // - axiosInstance가 refresh를 시도했지만 refresh_token/device_id도
        //   유효하지 않은 경우(다른 디바이스 로그인, 만료 등) 401이 여기까지 전달됨
        if (err.response?.status === 401) {
          // 메모리에 남은 accessToken 정리 (로그인 상태 초기화)
          clearAccessToken();

          // 로그인 안 된 메인화면으로 이동
          // replace: true → 뒤로가기로 다시 /posts에 못 들어오게 히스토리 대체
          navigate('/', { replace: true });
          return; // 아래 setError 실행 방지
        }

        setError(err.response?.data?.message || '게시글을 불러오지 못했습니다.');
        console.error(err);
      } finally {
        setLoading(false);
      }
    };
    fetchPosts();
  }, [page, myPostsOnly, currentUserId, sortBy]);

  const scopedPosts = posts.filter((post) => {
    if (myPostsOnly) return true;
    return post.authorId === currentUserId || !activeMatchedPostIds.has(post.postId);
  });

  const sortedPosts = [...scopedPosts].sort((a, b) => {
    if (sortBy === '최신순') {
      return new Date(b.createdAt || b.createAt).getTime() - new Date(a.createdAt || a.createAt).getTime();
    }

    if (sortBy === '만남시간 빠른 순') {
      return new Date(a.meetAt).getTime() - new Date(b.meetAt).getTime();
    }

    return b.authorDeposit - a.authorDeposit;
  });

  return (
      <div>
        <div className="mb-8">
          <h1 className="mb-3 text-3xl font-bold text-[#212121]">
            {myPostsOnly ? '내가 작성한 게시물' : '밥 같이 먹을 사람 구해요'}
          </h1>
          <p className="text-[#616161]">{myPostsOnly ? '내가 작성한 게시글 전체' : '모집중 게시글'}</p>
        </div>

        <div className="mb-6 flex items-center justify-between">
          <div />
          <div className="flex items-center gap-4">
            <select
                value={sortBy}
                onChange={(event) => setSortBy(event.target.value as SortOption)}
                className="rounded-lg border border-[#e0e0e0] px-4 py-2 text-sm focus:border-transparent focus:outline-none focus:ring-2 focus:ring-[#d84315]"
            >
              <option>책임비 높은 순</option>
              <option>만남시간 빠른 순</option>
              <option>최신순</option>
            </select>

            <Link
                to="/posts/new"
                className="flex items-center gap-2 rounded-xl bg-[#d84315] px-6 py-2.5 font-semibold text-white shadow-md transition-all hover:bg-[#bf360c] hover:shadow-lg"
            >
              <Plus size={20} />
              게시글 작성
            </Link>
          </div>
        </div>

        {error && (
            <div className="mb-6 flex items-start gap-2 rounded-lg border border-[#ef5350] bg-[#ffebee] px-4 py-3">
              <AlertCircle size={18} className="mt-0.5 text-[#c62828]" />
              <span className="text-sm text-[#c62828]">{error}</span>
            </div>
        )}

        {loading ? (
            <div className="space-y-3">
              {[1, 2, 3, 4, 5, 6].map((n) => (
                  <div key={n} className="h-28 animate-pulse rounded-xl border border-[#e0e0e0] bg-white p-5">
                    <div className="mb-4 h-5 w-1/4 rounded bg-gray-200" />
                    <div className="mb-2 h-4 w-2/3 rounded bg-gray-200" />
                    <div className="h-4 w-1/2 rounded bg-gray-200" />
                  </div>
              ))}
            </div>
        ) : (
            <div className="space-y-3">
              {sortedPosts.length === 0 ? (
                  <div className="rounded-xl border border-[#e0e0e0] bg-white p-10 text-center text-[#9e9e9e]">
                    {myPostsOnly ? '내가 작성한 게시글이 없습니다.' : '모집중인 게시글이 없습니다.'}
                  </div>
              ) : sortedPosts.map((post) => (
                  <Link
                      key={post.postId}
                      to={`/posts/${post.postId}`}
                      className="block rounded-xl border border-[#e0e0e0] bg-white p-4 transition-all hover:border-[#d84315] hover:shadow-md sm:p-5"
                  >
                    <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between sm:gap-5">
                      <div className="min-w-0 flex-1">
                        <div className="mb-2 flex items-center gap-2">
                    <span className={`rounded px-2.5 py-1 text-xs font-semibold ${statusClasses[post.status] || 'bg-[#f5f5f5] text-[#757575]'}`}>
                      {statusLabels[post.status] || post.status}
                    </span>
                          <span className="text-xs text-[#9e9e9e]">{getTimeAgo(post.createdAt || post.createAt)}</span>
                        </div>

                        <h3 className="mb-3 truncate text-lg font-bold text-[#212121]">
                          {post.placeName} 에서 만나요
                        </h3>

                        <div className="flex flex-wrap items-center gap-x-5 gap-y-2 text-sm text-[#616161]">
                    <span className="flex items-center gap-1.5">
                      <MapPin size={16} className="text-[#d84315]" />
                      {post.placeName}
                    </span>
                          <span className="flex items-center gap-1.5">
                      <Clock size={16} className="text-[#d84315]" />
                            {new Date(post.meetAt).toLocaleDateString()} {formatTime(post.meetAt)}
                    </span>
                          <span className="flex items-center gap-1.5">
                      <User size={16} className="text-[#d84315]" />
                            {post.authorNickname}
                    </span>
                          <span className="flex items-center gap-1.5">
                      <Thermometer size={16} className="text-[#d84315]" />
                            {formatMannerTemperature(post.authorMannerTemperature)}
                    </span>
                          <span className="flex items-center gap-1.5">
                      <Users size={16} className="text-[#d84315]" />
                            {formatParticipantCount(post.currentApplicants, post.maxApplicants)}
                    </span>
                        </div>
                      </div>

                      <div className="flex shrink-0 flex-row items-center justify-between border-t border-[#f0f0f0] pt-4 sm:w-32 sm:flex-col sm:items-end sm:border-l sm:border-t-0 sm:pl-5 sm:pt-0">
                        <span className="text-xs font-semibold text-[#9e9e9e]">책임비</span>
                        <span className="mt-1 text-2xl font-bold text-[#d84315]">
                    {post.authorDeposit.toLocaleString()}P
                  </span>
                      </div>
                    </div>
                  </Link>
              ))}
            </div>
        )}

        {!myPostsOnly && totalPages > 1 && (
            <div className="mt-8 flex items-center justify-center gap-2">
              <button
                  disabled={page === 0}
                  onClick={() => setPage((prev) => prev - 1)}
                  className="rounded border border-[#e0e0e0] px-3 py-1.5 text-sm hover:bg-[#f5f5f5] disabled:opacity-50"
              >
                &lt;
              </button>
              {[...Array(totalPages)].map((_, index) => (
                  <button
                      key={index}
                      onClick={() => setPage(index)}
                      className={`rounded px-3 py-1.5 text-sm ${
                          page === index ? 'bg-[#d84315] text-white' : 'border border-[#e0e0e0] hover:bg-[#f5f5f5]'
                      }`}
                  >
                    {index + 1}
                  </button>
              ))}
              <button
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

function formatTime(dateStr: string) {
  return new Date(dateStr).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function getTimeAgo(dateStr: string) {
  const now = new Date();
  const past = new Date(dateStr);
  const diffMs = now.getTime() - past.getTime();
  const diffMins = Math.floor(diffMs / 60000);
  if (diffMins < 1) return '방금 전';
  if (diffMins < 60) return `${diffMins}분 전`;
  const diffHours = Math.floor(diffMins / 60);
  if (diffHours < 24) return `${diffHours}시간 전`;
  return past.toLocaleDateString();
}

function formatParticipantCount(currentApplicants?: number, maxApplicants?: number) {
  const safeMax = maxApplicants && maxApplicants > 1 ? maxApplicants : 2;
  const safeCurrent = Math.min(currentApplicants && currentApplicants > 0 ? currentApplicants : 1, safeMax);
  return `${safeCurrent}/${safeMax}`;
}

function formatMannerTemperature(mannerTemperature?: number | null) {
  if (mannerTemperature === null || mannerTemperature === undefined) {
    return '36.5°C';
  }

  return `${Number(mannerTemperature).toFixed(1)}°C`;
}
