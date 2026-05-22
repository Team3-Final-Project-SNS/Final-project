import { useState, useEffect } from 'react';
import { useParams, useNavigate, Link } from 'react-router';
import { ArrowLeft, MapPin, Clock, User, AlertCircle, Loader2 } from 'lucide-react';
import { getPost, GetPostResponse } from '../../api/postApi';
import axiosInstance from '../../api/axiosInstance'; // 임시로 matchApi 대신 사용 (아직 안만듦)

export default function PostDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [post, setPost] = useState<GetPostResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showMatchModal, setShowMatchModal] = useState(false);
  const [userPoints, setUserPoints] = useState(0);
  const [matchLoading, setMatchLoading] = useState(false);

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      setError('');
      try {
        const [postRes, userRes] = await Promise.all([
          getPost(Number(id)),
          axiosInstance.get('/api/v1/users/me')
        ]);
        setPost(postRes.data.data);
        setUserPoints(userRes.data.data.point);
      } catch (err: any) {
        setError('게시글을 불러오는데 실패했습니다.');
        console.error(err);
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, [id]);

  const handleMatch = () => {
    if (userPoints < (post?.authorDeposit || 0)) {
      alert('포인트가 부족합니다.');
      return;
    }
    setShowMatchModal(true);
  };

  const confirmMatch = async () => {
    setMatchLoading(true);
    try {
      const res = await axiosInstance.post(`/api/v1/posts/${id}/matches`);
      const { matchId } = res.data.data;
      navigate(`/matches/${matchId}`);
    } catch (err: any) {
      alert(err.response?.data?.message || '매칭 신청에 실패했습니다.');
    } finally {
      setMatchLoading(false);
      setShowMatchModal(false);
    }
  };

  if (loading) return (
    <div className="flex flex-col items-center justify-center py-20">
      <Loader2 className="animate-spin text-[#d84315] mb-4" size={40} />
      <p className="text-[#616161]">정보를 불러오는 중...</p>
    </div>
  );

  if (!post) return (
    <div className="text-center py-20">
      <p className="text-[#616161]">게시글을 찾을 수 없습니다.</p>
      <Link to="/posts" className="text-[#d84315] mt-4 inline-block">목록으로 돌아가기</Link>
    </div>
  );

  const afterPoints = userPoints - post.authorDeposit;

  return (
      <div className="max-w-2xl mx-auto">
        <button
            onClick={() => navigate(-1)}
            className="flex items-center gap-2 text-[#616161] hover:text-[#d84315] mb-6"
        >
          <ArrowLeft size={20} />
          목록으로
        </button>

        {error && (
            <div className="mb-6 bg-[#ffebee] border border-[#ef5350] rounded-lg px-4 py-3 flex items-start gap-2">
              <AlertCircle size={18} className="text-[#c62828] mt-0.5" />
              <span className="text-[#c62828] text-sm">{error}</span>
            </div>
        )}

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
            <span className="text-2xl font-bold text-[#d84315]">{post.authorDeposit.toLocaleString()}P</span>
          </div>

          <p className="text-[#424242] leading-relaxed mb-6 whitespace-pre-wrap">{post.content || '내용 없음'}</p>

          <div className="bg-[#fafafa] rounded-lg p-4 space-y-3 mb-6">
            <div className="flex items-center gap-3">
              <MapPin size={20} className="text-[#d84315]" />
              <div>
                <p className="text-xs text-[#9e9e9e]">장소</p>
                <p className="font-medium text-[#212121]">{post.placeName}</p>
              </div>
            </div>

            <div className="flex items-center gap-3">
              <Clock size={20} className="text-[#d84315]" />
              <div>
                <p className="text-xs text-[#9e9e9e]">시간</p>
                <p className="font-medium text-[#212121]">
                    {new Date(post.meetAt).toLocaleString()}
                </p>
              </div>
            </div>

            <div className="flex items-center gap-3">
              <User size={20} className="text-[#d84315]" />
              <div>
                <p className="text-xs text-[#9e9e9e]">작성자</p>
                <p className="font-medium text-[#212121]">{post.authorNickname}</p>
              </div>
            </div>
          </div>

          <div className="text-sm text-[#9e9e9e] mb-6">
              작성일: {new Date(post.createdAt).toLocaleString()}
          </div>

          {post.status === 'OPEN' && !post.isMine && (
              <div className="bg-[#fff3e0] border border-[#ff9800] rounded-lg p-4 mb-6">
                <h3 className="font-semibold text-[#212121] mb-2">매칭 신청</h3>
                <p className="text-sm text-[#616161] mb-3">
                  신청 시 {post.authorDeposit.toLocaleString()}P가 예치됩니다. 만남 완료 후 반환됩니다.
                </p>
                <p className="text-sm text-[#616161] mb-3">
                  현재 잔액: <strong className="text-[#d84315]">{userPoints.toLocaleString()}P</strong> → 신청 후 잔액:{' '}
                  <strong className="text-[#4caf50]">{afterPoints.toLocaleString()}P</strong>
                </p>

                <button
                    onClick={handleMatch}
                    className="w-full bg-[#d84315] text-white py-4 rounded-xl font-bold text-lg hover:bg-[#bf360c] transition-all shadow-md hover:shadow-lg"
                >
                  {post.authorDeposit.toLocaleString()}P 예치하고 신청하기
                </button>

                <p className="text-xs text-[#9e9e9e] text-center mt-3">
                  신청 후 취소 시 책임비의 50%가 차감될 수 있습니다.
                </p>
              </div>
          )}

          {post.isMine && post.status === 'OPEN' && (
              <div className="flex gap-4">
                  <button 
                    onClick={() => navigate(`/posts/${id}/edit`)}
                    className="flex-1 py-3 border border-[#e0e0e0] rounded-xl font-semibold text-[#616161] hover:bg-[#f5f5f5]"
                  >
                      수정하기
                  </button>
                  <button 
                    onClick={async () => {
                        if(confirm('정말 삭제하시겠습니까?')) {
                            try {
                                await axiosInstance.delete(`/api/v1/posts/${id}`);
                                navigate('/posts');
                            } catch(e) { alert('삭제 실패'); }
                        }
                    }}
                    className="flex-1 py-3 border border-red-200 text-red-500 rounded-xl font-semibold hover:bg-red-50"
                  >
                      삭제하기
                  </button>
              </div>
          )}
        </div>

        {showMatchModal && (
            <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
              <div className="bg-white rounded-lg p-6 max-w-md w-full">
                <h2 className="text-xl font-bold text-[#212121] mb-4">매칭 신청 확인</h2>
                <p className="text-[#616161] mb-6">
                  정말 신청하시겠습니까? {post.authorDeposit.toLocaleString()}P가 예치되며, 만남 완료 시 반환됩니다.
                </p>

                <div className="flex gap-3">
                  <button
                      onClick={() => setShowMatchModal(false)}
                      disabled={matchLoading}
                      className="flex-1 py-3 border border-[#e0e0e0] rounded-lg font-semibold text-[#616161] hover:bg-[#f5f5f5]"
                  >
                    취소
                  </button>
                  <button
                      onClick={confirmMatch}
                      disabled={matchLoading}
                      className="flex-1 py-3 bg-[#d84315] text-white rounded-lg font-semibold hover:bg-[#bf360c] flex items-center justify-center"
                  >
                    {matchLoading ? <Loader2 className="animate-spin" size={20} /> : '신청하기'}
                  </button>
                </div>
              </div>
            </div>
        )}
      </div>
  );
}
