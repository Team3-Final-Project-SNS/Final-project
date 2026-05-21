import { useState, useEffect } from 'react';
import { Link } from 'react-router';
import { MapPin, Clock, Plus, AlertCircle } from 'lucide-react';
import { getPosts, PostItemResponse, PostStatus } from '../../api/postApi';

type FilterStatus = '전체' | 'OPEN' | 'MATCHED';

export default function PostListPage() {
  const [posts, setPosts] = useState<PostItemResponse[]>([]);
  const [filter, setFilter] = useState<FilterStatus>('전체');
  const [sortBy, setSortBy] = useState('책임비 높은 순');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);

  useEffect(() => {
    const fetchPosts = async () => {
      setLoading(true);
      setError('');
      try {
        // 백엔드 명세상 status 필터는 필수(기본 OPEN)이거나 선택임.
        // 여기서는 '전체'일 경우 null을 보내거나 특정 로직 필요. 
        // PostController는 @RequestParam(defaultValue = "OPEN") PostStatus status 를 가짐.
        const statusParam = filter === '전체' ? 'OPEN' : filter;
        const res = await getPosts(statusParam as PostStatus, page, 20);
        setPosts(res.data.data.content);
        setTotalPages(res.data.data.totalPages);
      } catch (err: any) {
        setError('게시글을 불러오는데 실패했습니다.');
        console.error(err);
      } finally {
        setLoading(false);
      }
    };
    fetchPosts();
  }, [filter, page]);

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

  return (
      <div>
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-[#212121] mb-3">밥 같이 먹을 사람 구해요 🍚</h1>
          <p className="text-[#616161]">전체 게시글</p>
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
                  {status === '전체' ? '전체 (모집중)' : status}
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
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {[1, 2, 3, 4, 5, 6].map((n) => (
                  <div key={n} className="bg-white border border-[#e0e0e0] rounded-xl p-5 h-48 animate-pulse">
                    <div className="h-6 bg-gray-200 rounded w-1/4 mb-4"></div>
                    <div className="h-4 bg-gray-200 rounded w-3/4 mb-2"></div>
                    <div className="h-4 bg-gray-200 rounded w-1/2 mb-4"></div>
                  </div>
              ))}
            </div>
        ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {posts.map((post) => (
                  <Link
                      key={post.postId}
                      to={`/posts/${post.postId}`}
                      className="bg-white border border-[#e0e0e0] rounded-xl p-5 hover:shadow-lg transition-all hover:border-[#d84315]"
                  >
                    <div className="flex items-start justify-between mb-3">
                  <span
                      className={`px-3 py-1 rounded text-xs font-semibold ${
                          post.status === 'OPEN'
                              ? 'bg-[#4caf50] text-white'
                              : 'bg-[#ff9800] text-white'
                      }`}
                  >
                    {post.status}
                  </span>
                      <span className="text-lg font-bold text-[#d84315]">{post.authorDeposit.toLocaleString()}P</span>
                    </div>

                    <h3 className="font-semibold text-[#212121] mb-2">{post.title}</h3>
                    <p className="text-sm text-[#616161] mb-4 line-clamp-2">{post.content}</p>

                    <div className="space-y-1.5">
                      <div className="flex items-center gap-2 text-sm text-[#616161]">
                        <MapPin size={16} className="text-[#d84315]" />
                        <span>{post.placeName}</span>
                      </div>
                      <div className="flex items-center gap-2 text-sm text-[#616161]">
                        <Clock size={16} className="text-[#d84315]" />
                        <span>{new Date(post.meetAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
                      </div>
                    </div>

                    <div className="mt-4 pt-3 border-t border-[#f5f5f5] flex items-center justify-between text-xs text-[#9e9e9e]">
                      <span>{post.authorNickname} ({post.authorMajor} {post.authorStudentNumber}학번)</span>
                      <span>{getTimeAgo(post.createdAt)}</span>
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
