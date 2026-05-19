import { useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router';
import { ArrowLeft, MapPin, Clock, User } from 'lucide-react';

export default function PostDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [showMatchModal, setShowMatchModal] = useState(false);

  const post = {
    id: 1,
    title: '오늘 점심 학생식당 같이 가실 분!',
    content: '배고픈데 혼자 먹기 그래서 😊 조용히 같이 밥 먹을 수 있는 분이면 누구든지 환영해요. 학생식당 1층 입구 앞에서 만나요!',
    location: '학생식당 1층',
    time: '오후 12:30',
    status: 'OPEN',
    points: 3000,
    author: '밥먹자',
    createdAt: '2026년 5월 14일',
  };

  const handleMatch = () => {
    setShowMatchModal(true);
  };

  const confirmMatch = () => {
    navigate('/matches');
  };

  return (
      <div className="max-w-2xl mx-auto">
        <button
            onClick={() => navigate(-1)}
            className="flex items-center gap-2 text-[#616161] hover:text-[#d84315] mb-6"
        >
          <ArrowLeft size={20} />
          목록으로
        </button>

        <div className="bg-white border border-[#e0e0e0] rounded-2xl p-8 shadow-sm">
          <div className="flex items-start justify-between mb-4">
          <span
              className={`px-3 py-1 rounded text-xs font-semibold ${
                  post.status === 'OPEN'
                      ? 'bg-[#4caf50] text-white'
                      : 'bg-[#ff9800] text-white'
              }`}
          >
            {post.status}
          </span>
            <span className="text-2xl font-bold text-[#d84315]">{post.points.toLocaleString()}P</span>
          </div>

          <h1 className="text-2xl font-bold text-[#212121] mb-4">{post.title}</h1>

          <p className="text-[#424242] leading-relaxed mb-6 whitespace-pre-wrap">{post.content}</p>

          <div className="bg-[#fafafa] rounded-lg p-4 space-y-3 mb-6">
            <div className="flex items-center gap-3">
              <MapPin size={20} className="text-[#d84315]" />
              <div>
                <p className="text-xs text-[#9e9e9e]">장소</p>
                <p className="font-medium text-[#212121]">{post.location}</p>
              </div>
            </div>

            <div className="flex items-center gap-3">
              <Clock size={20} className="text-[#d84315]" />
              <div>
                <p className="text-xs text-[#9e9e9e]">시간</p>
                <p className="font-medium text-[#212121]">{post.time}</p>
              </div>
            </div>

            <div className="flex items-center gap-3">
              <User size={20} className="text-[#d84315]" />
              <div>
                <p className="text-xs text-[#9e9e9e]">작성자</p>
                <p className="font-medium text-[#212121]">{post.author}</p>
              </div>
            </div>
          </div>

          <div className="text-sm text-[#9e9e9e] mb-6">{post.createdAt}</div>

          {post.status === 'OPEN' && (
              <div className="bg-[#fff3e0] border border-[#ff9800] rounded-lg p-4 mb-6">
                <h3 className="font-semibold text-[#212121] mb-2">매칭 신청</h3>
                <p className="text-sm text-[#616161] mb-3">
                  신청 시 {post.points.toLocaleString()}P가 예치됩니다. 만남 완료 후 반환됩니다.
                </p>
                <p className="text-sm text-[#616161] mb-3">
                  현재 잔액: <strong className="text-[#d84315]">8,500P</strong> → 신청 후 잔액:{' '}
                  <strong className="text-[#4caf50]">5,500P</strong>
                </p>

                <button
                    onClick={handleMatch}
                    className="w-full bg-[#d84315] text-white py-4 rounded-xl font-bold text-lg hover:bg-[#bf360c] transition-all shadow-md hover:shadow-lg"
                >
                  {post.points.toLocaleString()}P 예치하고 신청하기
                </button>

                <p className="text-xs text-[#9e9e9e] text-center mt-3">
                  신청 후 1명 · 비매칭 취소 등시 신청 방지
                </p>
              </div>
          )}
        </div>

        {showMatchModal && (
            <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
              <div className="bg-white rounded-lg p-6 max-w-md w-full">
                <h2 className="text-xl font-bold text-[#212121] mb-4">매칭 신청 확인</h2>
                <p className="text-[#616161] mb-6">
                  정말 신청하시겠습니까? {post.points.toLocaleString()}P가 예치되며, 만남 완료 시 반환됩니다.
                </p>

                <div className="flex gap-3">
                  <button
                      onClick={() => setShowMatchModal(false)}
                      className="flex-1 py-3 border border-[#e0e0e0] rounded-lg font-semibold text-[#616161] hover:bg-[#f5f5f5]"
                  >
                    취소
                  </button>
                  <button
                      onClick={confirmMatch}
                      className="flex-1 py-3 bg-[#d84315] text-white rounded-lg font-semibold hover:bg-[#bf360c]"
                  >
                    신청하기
                  </button>
                </div>
              </div>
            </div>
        )}
      </div>
  );
}
