import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router';
import { createPost } from '../../api/postApi';
import axiosInstance from '../../api/axiosInstance';
import { AlertCircle, Loader2 } from 'lucide-react';

export default function PostCreatePage() {
  const navigate = useNavigate();
  const [placeName, setPlaceName] = useState('');
  const [placeLat, setPlaceLat] = useState(37.5665);
  const [placeLng, setPlaceLng] = useState(126.9780);
  const [date, setDate] = useState(new Date().toISOString().split('T')[0]);
  const [time, setTime] = useState('12:30');
  const [content, setContent] = useState('');
  const [points, setPoints] = useState(1000);
  const [userPoints, setUserPoints] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const fetchUser = async () => {
      try {
        const res = await axiosInstance.get('/api/v1/users/me');
        setUserPoints(res.data.data.point);
      } catch (e) {
        console.error('Failed to fetch user info');
      }
    };
    fetchUser();
  }, []);

  const afterPoints = userPoints - points;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!placeName || !date || !time) {
      setError('필수 정보를 입력해주세요.');
      return;
    }

    setLoading(true);
    setError('');
    try {
      const meetAt = `${date}T${time}:00`;
      await createPost({
        meetAt,
        placeName,
        placeLat,
        placeLng,
        content,
        authorDeposit: points,
      });
      navigate('/posts');
    } catch (err: any) {
      setError(err.response?.data?.message || '게시글 작성에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const pointOptions = [1000, 2000, 3000, 5000];

  return (
      <div className="max-w-2xl mx-auto">
        <h1 className="text-3xl font-bold text-[#212121] mb-2 text-center">밥 같이 먹을 사람 구하기 🍚</h1>
        <p className="text-center text-[#616161] mb-8">간단한 정보만 입력하면 매칭이 시작됩니다</p>

        <form onSubmit={handleSubmit} className="bg-white border border-[#e0e0e0] rounded-2xl p-8 shadow-sm">
          {error && (
            <div className="mb-6 bg-[#ffebee] border border-[#ef5350] rounded-lg px-4 py-3 flex items-start gap-2">
              <AlertCircle size={18} className="text-[#c62828] mt-0.5" />
              <span className="text-[#c62828] text-sm">{error}</span>
            </div>
          )}

          <div className="mb-6">
            <h2 className="text-lg font-semibold text-[#212121] mb-4">모임 정보</h2>

            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-[#424242] mb-2">
                  만남 장소
                </label>
                <input
                    type="text"
                    value={placeName}
                    onChange={(e) => setPlaceName(e.target.value)}
                    placeholder="예: 학생식당 1층, 공학관 카페"
                    className="w-full px-4 py-3 border border-[#e0e0e0] rounded-lg focus:outline-none focus:ring-2 focus:ring-[#d84315] focus:border-transparent"
                    required
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-[#424242] mb-2">
                    날짜
                  </label>
                  <input
                      type="date"
                      value={date}
                      onChange={(e) => setDate(e.target.value)}
                      className="w-full px-4 py-3 border border-[#e0e0e0] rounded-lg focus:outline-none focus:ring-2 focus:ring-[#d84315] focus:border-transparent"
                      required
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-[#424242] mb-2">
                    시간
                  </label>
                  <input
                      type="time"
                      value={time}
                      onChange={(e) => setTime(e.target.value)}
                      className="w-full px-4 py-3 border border-[#e0e0e0] rounded-lg focus:outline-none focus:ring-2 focus:ring-[#d84315] focus:border-transparent"
                      required
                  />
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium text-[#424242] mb-2">
                  한마디 (선택)
                </label>
                <textarea
                    value={content}
                    onChange={(e) => setContent(e.target.value)}
                    placeholder="간단히 소개해주세요"
                    rows={4}
                    className="w-full px-4 py-3 border border-[#e0e0e0] rounded-lg focus:outline-none focus:ring-2 focus:ring-[#d84315] focus:border-transparent resize-none"
                />
              </div>
            </div>
          </div>

          <div className="mb-6">
            <h2 className="text-lg font-semibold text-[#212121] mb-2">책임비 설정</h2>
            <p className="text-sm text-[#616161] mb-4">약속 이행의 증거</p>

            <div className="flex gap-2 flex-wrap mb-4">
              {pointOptions.map((option) => (
                  <button
                      key={option}
                      type="button"
                      onClick={() => setPoints(option)}
                      className={`px-6 py-3 rounded-lg font-semibold transition-colors ${
                          points === option
                              ? 'bg-[#d84315] text-white'
                              : 'bg-white border border-[#e0e0e0] text-[#616161] hover:border-[#d84315]'
                      }`}
                  >
                    {option.toLocaleString()}P
                  </button>
              ))}
              <input 
                type="number"
                placeholder="직접입력"
                value={points}
                onChange={(e) => setPoints(Number(e.target.value))}
                className="px-4 py-3 border border-[#e0e0e0] rounded-lg w-32 focus:outline-none focus:ring-2 focus:ring-[#d84315]"
              />
            </div>

            <div className="bg-[#fff3e0] rounded-lg p-4">
              <p className="text-sm text-[#616161]">
                현재 잔액 <strong className="text-[#d84315]">{userPoints.toLocaleString()}P</strong> → 신청 후 잔액:{' '}
                <strong className="text-[#4caf50]">{afterPoints.toLocaleString()}P</strong>
              </p>
            </div>
          </div>

          <button
              type="submit"
              disabled={loading}
              className="w-full bg-[#d84315] text-white py-4 rounded-xl font-bold text-lg hover:bg-[#bf360c] transition-all shadow-md hover:shadow-lg disabled:bg-[#e0e0e0] flex items-center justify-center"
          >
            {loading ? <Loader2 className="animate-spin" /> : '게시글 올리기'}
          </button>
        </form>
      </div>
  );
}
