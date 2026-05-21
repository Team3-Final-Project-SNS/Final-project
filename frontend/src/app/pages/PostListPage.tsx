import { useState, useEffect } from 'react';
import { Link, useSearchParams } from 'react-router';
import { MapPin, Clock, Plus, AlertCircle, User } from 'lucide-react';
import { getPosts, PostItemResponse, PostStatus } from '../../api/postApi';
import { getUserMe } from '../../api/userApi';

type FilterStatus = '전체' | 'OPEN' | 'MATCHED';

const uniquePostsById = (posts: PostItemResponse[]) =>
    [...new Map(posts.map((post) => [post.postId, post])).values()];

export default function PostListPage() {
  const [searchParams] = useSearchParams();
  const myPostsOnly = searchParams.get('mine') === '1';
  const [posts, setPosts] = useState<PostItemResponse[]>([]);
  const [filter, setFilter] = useState<FilterStatus>('전체');
  const [sortBy, setSortBy] = useState('책임비 높은 순');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [currentUserId, setCurrentUserId] = useState<number | null>(null);

  useEffect(() => {
    const fetchPosts = async () => {
      setLoading(true);
      setError('');
      try {
        let userId = currentUserId;
        if (myPostsOnly && userId === null) {
          const userRes = await getUserMe();
          userId = userRes.data.data.userId;
          setCurrentUserId(userId);
        }

        if (filter === '전체') {
          const [openRes, matchedRes] = await Promise.all([
            getPosts('OPEN', page, 20),
            getPosts('MATCHED', page, 20),
          ]);
          setPosts(uniquePostsById([...openRes.data.data.content, ...matchedRes.data.data.content]));
          setTotalPages(Math.max(openRes.data.data.totalPages, matchedRes.data.data.totalPages));
        } else {
          const res = await getPosts(filter as PostStatus, page, 20);
          setPosts(res.data.data.content);
          setTotalPages(res.data.data.totalPages);
        }
      } catch (err: any) {
        setError('게시글을 불러오는데 실패했습니다.');
        console.error(err);
      } finally {
        setLoading(false);
      }
    };
    fetchPosts();
  }, [filter, page, myPostsOnly, currentUserId]);

  const formatTime = (dateStr: string) => {
    const date = new Date(dateStr);
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  };

  const getTimeAgo = (dateStr: string) => {
    const now = new Date();
    const past = new Date(dateStr);
    const diffMs = now.getTime() - past.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    if (diffMins < 1) return '방금 전';
    if (diffMins < 60) return `${diffMins}분 전`;
    const diffHours = Math.floor(diffMins / 60);
    if (diffHours < 24) return `${diffHours}시간 전`;
    return past.toLocaleDateString();
  };

  const visiblePosts = filter === '전체'
      ? posts.filter((post) => post.status === 'OPEN' || post.status === 'MATCHED')
      : posts.filter((post) => post.status === filter);

  const scopedPosts = myPostsOnly && currentUserId !== null
      ? visiblePosts.filter((post) => post.authorId === currentUserId)
      : visiblePosts;

  const sortedPosts = [...scopedPosts].sort((a, b) => {
    if (sortBy === '최신순') {
      return new Date(b.createAt).getTime() - new Date(a.createAt).getTime();
    }

    return b.authorDeposit - a.authorDeposit;
  });

  return (
      <div>
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-[#212121] mb-3">
            {myPostsOnly ? '내가 작성한 게시물' : '밥 같이 먹을 사람 구해요 🍚'}
          </h1>
          <p className="text-[#616161]">{myPostsOnly ? '내 게시글' : '전체 게시글'}</p>
        </div>

        <div className="flex items-center justify-between mb-6">
          <div className="flex gap-2">
            {(['전체', 'OPEN', 'MATCHED'] as FilterStatus[]).map((status) => (
                <button
                    key={status}
                    onClick={() => {
                      setFilter(status);
                      setPage(0);
                    }}
                    className={`px-4 py-2 rounded-full text-sm font-medium transition-colors ${
                        filter === status
                            ? 'bg-[#d84315] text-white'
                            : 'bg-white border border-[#e0e0e0] text-[#616161] hover:border-[#d84315]'
                    }`}
                >
                  {status === '전체' ? '전체' : status === 'OPEN' ? '모집중' : '매칭됨'}
                </button>
            ))}
          </div>

          <div className="flex items-center gap-4">
            <select
                value={sortBy}
                onChange={(e) => setSortBy(e.target.value)}
                className="px-4 py-2 border border-[#e0e0e0] rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#d84315] focus:border-transparent"
            >
              <option>책임비 높은 순</option>
              <option>최신순</option>
            </select>

            <Link
                to="/posts/new"
                className="flex items-center gap-2 px-6 py-2.5 bg-[#d84315] text-white rounded-xl font-semibold hover:bg-[#bf360c] transition-all shadow-md hover:shadow-lg"
            >
              <Plus size={20} />
              게시글 작성
            </Link>
          </div>
        </div>

        {error && (
            <div className="mb-6 bg-[#ffebee] border border-[#ef5350] rounded-lg px-4 py-3 flex items-start gap-2">
              <AlertCircle size={18} className="text-[#c62828] mt-0.5" />
              <span className="text-[#c62828] text-sm">{error}</span>
            </div>
        )}

        {loading ? (
            <div className="space-y-3">
              {[1, 2, 3, 4, 5, 6].map((n) => (
                  <div key={n} className="h-28 rounded-xl border border-[#e0e0e0] bg-white p-5 animate-pulse">
                    <div className="mb-4 h-5 w-1/4 rounded bg-gray-200"></div>
                    <div className="mb-2 h-4 w-2/3 rounded bg-gray-200"></div>
                    <div className="h-4 w-1/2 rounded bg-gray-200"></div>
                  </div>
              ))}
            </div>
        ) : (
            <div className="space-y-3">
              {sortedPosts.length === 0 ? (
                  <div className="rounded-xl border border-[#e0e0e0] bg-white p-10 text-center text-[#9e9e9e]">
                    {myPostsOnly ? '내가 작성한 게시글이 없습니다.' : '해당 상태의 게시글이 없습니다.'}
                  </div>
              ) : sortedPosts.map((post) => (
                  <Link
                      key={post.postId}
                      to={`/posts/${post.postId}`}
                      className="block rounded-xl border border-[#e0e0e0] bg-white p-5 transition-all hover:border-[#d84315] hover:shadow-md"
                  >
                    <div className="flex items-center justify-between gap-5">
                      <div className="min-w-0 flex-1">
                        <div className="mb-2 flex items-center gap-2">
                          <span
                              className={`rounded px-2.5 py-1 text-xs font-semibold ${
                                  post.status === 'OPEN'
                                      ? 'bg-[#e8f5e9] text-[#2e7d32]'
                                      : 'bg-[#fff3e0] text-[#ef6c00]'
                              }`}
                          >
                            {post.status === 'OPEN' ? '모집중' : '매칭됨'}
                          </span>
                          <span className="text-xs text-[#9e9e9e]">{getTimeAgo(post.createAt)}</span>
                        </div>

                        <h3 className="mb-3 truncate text-lg font-bold text-[#212121]">
                          {post.placeName} 같이 먹어요
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
                            {post.authorNickname} ({post.authorMajor} {post.authorStudentNumber}학번)
                          </span>
                        </div>
                      </div>

                      <div className="flex w-32 shrink-0 flex-col items-end border-l border-[#f0f0f0] pl-5">
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
      </div>
  );
}
