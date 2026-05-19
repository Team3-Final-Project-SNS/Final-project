import { useState } from 'react';
import { Link } from 'react-router';
import { MapPin, Clock, Plus } from 'lucide-react';

type PostStatus = '전체' | '오늘' | '내일';

interface Post {
  id: number;
  title: string;
  content: string;
  location: string;
  time: string;
  status: 'OPEN' | 'MATCHED';
  points: number;
  author: string;
  timeAgo: string;
}

export default function PostListPage() {
  const [filter, setFilter] = useState<PostStatus>('전체');
  const [sortBy, setSortBy] = useState('책임비 높은 순');

  const mockPosts: Post[] = [
    {
      id: 1,
      title: '오늘 점심 학생식당 같이 가실 분!',
      content: '배고픈데 혼자 먹기 그래서 😊',
      location: '학생식당 1층',
      time: '오후 12:30',
      status: 'OPEN',
      points: 3000,
      author: '밥먹자',
      timeAgo: '5분 전',
    },
    {
      id: 2,
      title: '저녁 같이 드실 분 구해요',
      content: '초롱한 분이면 좋겠어요',
      location: '기숙사 앞 식당',
      time: '오후 6:00',
      status: 'OPEN',
      points: 2000,
      author: '조용해',
      timeAgo: '22분 전',
    },
    {
      id: 3,
      title: '점심 파트너 구합니다',
      content: '이미 매칭 완료된 게시글',
      location: '공학관 카페',
      time: '낮 12:00',
      status: 'MATCHED',
      points: 1500,
      author: '카페족',
      timeAgo: '1시간 전',
    },
  ];

  const filteredPosts = mockPosts;

  return (
    <div>
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-[#212121] mb-3">밥 같이 먹을 사람 구해요 🍚</h1>
        <p className="text-[#616161]">○○대학교 · 23개 게시글</p>
      </div>

      <div className="flex items-center justify-between mb-6">
        <div className="flex gap-2">
          {(['전체', '오늘', '내일'] as PostStatus[]).map((status) => (
            <button
              key={status}
              onClick={() => setFilter(status)}
              className={`px-4 py-2 rounded-full text-sm font-medium transition-colors ${
                filter === status
                  ? 'bg-[#d84315] text-white'
                  : 'bg-white border border-[#e0e0e0] text-[#616161] hover:border-[#d84315]'
              }`}
            >
              {status}
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
            <option>시간 임박순</option>
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

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {filteredPosts.map((post) => (
          <Link
            key={post.id}
            to={`/posts/${post.id}`}
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
              <span className="text-lg font-bold text-[#d84315]">{post.points.toLocaleString()}P</span>
            </div>

            <h3 className="font-semibold text-[#212121] mb-2">{post.title}</h3>
            <p className="text-sm text-[#616161] mb-4">{post.content}</p>

            <div className="space-y-1.5">
              <div className="flex items-center gap-2 text-sm text-[#616161]">
                <MapPin size={16} className="text-[#d84315]" />
                <span>{post.location}</span>
              </div>
              <div className="flex items-center gap-2 text-sm text-[#616161]">
                <Clock size={16} className="text-[#d84315]" />
                <span>{post.time}</span>
              </div>
            </div>

            <div className="mt-4 pt-3 border-t border-[#f5f5f5] flex items-center justify-between text-xs text-[#9e9e9e]">
              <span>닉네임: {post.author}</span>
              <span>{post.timeAgo}</span>
            </div>
          </Link>
        ))}
      </div>

      <div className="mt-8 flex items-center justify-center gap-2">
        <button className="px-3 py-1.5 border border-[#e0e0e0] rounded text-sm hover:bg-[#f5f5f5]">
          &lt;
        </button>
        <button className="px-3 py-1.5 bg-[#d84315] text-white rounded text-sm">1</button>
        <button className="px-3 py-1.5 border border-[#e0e0e0] rounded text-sm hover:bg-[#f5f5f5]">
          2
        </button>
        <button className="px-3 py-1.5 border border-[#e0e0e0] rounded text-sm hover:bg-[#f5f5f5]">
          3
        </button>
        <button className="px-3 py-1.5 border border-[#e0e0e0] rounded text-sm hover:bg-[#f5f5f5]">
          &gt;
        </button>
      </div>
    </div>
  );
}
