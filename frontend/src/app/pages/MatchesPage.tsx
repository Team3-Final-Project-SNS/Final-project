import { useState } from 'react';
import { Link } from 'react-router';
import { MapPin, Clock, MessageCircle, QrCode, CheckCircle } from 'lucide-react';

type MatchStatus = 'MATCHED' | 'COMPLETED' | 'CANCELED';

interface Match {
  id: number;
  status: MatchStatus;
  title: string;
  location: string;
  time: string;
  date: string;
  partner: string;
  points: number;
  pointsReturned?: boolean;
}

export default function MatchesPage() {
  const mockMatches: Match[] = [
    {
      id: 15,
      status: 'MATCHED',
      title: '오늘 점심 학생식당 같이 가실 분!',
      location: '학생식당 1층',
      time: '오후 12:30',
      date: '2026-05-14',
      partner: '밥먹자',
      points: 3000,
    },
    {
      id: 10,
      status: 'COMPLETED',
      title: '저녁 같이 드실 분',
      location: '기숙사 앞 식당',
      time: '오후 6:00',
      date: '2026-05-10',
      partner: '조용해',
      points: 2000,
      pointsReturned: true,
    },
    {
      id: 8,
      status: 'CANCELED',
      title: '점심 파트너 구합니다',
      location: '공학관 카페',
      time: '낮 12:00',
      date: '2026-05-08',
      partner: '카페족',
      points: 1500,
    },
  ];

  const [activeFilter, setActiveFilter] = useState<MatchStatus | '전체'>('전체');

  const filteredMatches = activeFilter === '전체'
      ? mockMatches
      : mockMatches.filter(m => m.status === activeFilter);

  const getStatusBadge = (status: MatchStatus) => {
    switch (status) {
      case 'MATCHED':
        return { text: 'MATCHED', color: 'bg-[#ff9800] text-white' };
      case 'COMPLETED':
        return { text: 'COMPLETED', color: 'bg-[#4caf50] text-white' };
      case 'CANCELED':
        return { text: 'CANCELED', color: 'bg-[#9e9e9e] text-white' };
    }
  };

  return (
      <div>
        <h1 className="text-3xl font-bold text-[#212121] mb-8">내 매칭</h1>

        <div className="flex gap-2 mb-6">
          {['전체', 'MATCHED', 'COMPLETED', 'CANCELED'].map((filter) => (
              <button
                  key={filter}
                  onClick={() => setActiveFilter(filter as MatchStatus | '전체')}
                  className={`px-4 py-2 rounded-full text-sm font-medium transition-colors ${
                      activeFilter === filter
                          ? 'bg-[#d84315] text-white'
                          : 'bg-white border border-[#e0e0e0] text-[#616161] hover:border-[#d84315]'
                  }`}
              >
                {filter}
              </button>
          ))}
        </div>

        <div className="space-y-4">
          {filteredMatches.map((match) => {
            const badge = getStatusBadge(match.status);

            return (
                <div
                    key={match.id}
                    className="bg-white border border-[#e0e0e0] rounded-xl p-6 hover:shadow-lg transition-all hover:border-[#d84315]"
                >
                  <div className="flex items-start justify-between mb-4">
                <span className={`px-3 py-1 rounded text-xs font-semibold ${badge.color}`}>
                  {badge.text}
                </span>
                    <span className="text-sm text-[#9e9e9e]">{match.date}</span>
                  </div>

                  <h3 className="font-semibold text-[#212121] mb-3">{match.title}</h3>

                  <div className="space-y-2 mb-4">
                    <div className="flex items-center gap-2 text-sm text-[#616161]">
                      <span className="font-medium text-[#424242]">상대방:</span> {match.partner}
                    </div>
                    <div className="flex items-center gap-2 text-sm text-[#616161]">
                      <MapPin size={16} className="text-[#d84315]" />
                      <span>{match.location}</span>
                    </div>
                    <div className="flex items-center gap-2 text-sm text-[#616161]">
                      <Clock size={16} className="text-[#d84315]" />
                      <span>{match.time}</span>
                    </div>
                    <div className="flex items-center gap-2 text-sm text-[#616161]">
                      <span className="font-medium text-[#424242]">예치 {match.points.toLocaleString()}P</span>
                      {match.pointsReturned && (
                          <span className="text-[#4caf50] text-xs flex items-center gap-1">
                      <CheckCircle size={14} /> 포인트 반환 완료
                    </span>
                      )}
                    </div>
                  </div>

                  <div className="flex gap-2">
                    {match.status === 'MATCHED' && (
                        <>
                          <Link
                              to={`/chat/${match.id}`}
                              className="flex-1 flex items-center justify-center gap-2 py-2.5 bg-white border border-[#e0e0e0] rounded-lg text-sm font-semibold text-[#616161] hover:bg-[#f5f5f5] transition-colors"
                          >
                            <MessageCircle size={16} />
                            채팅
                          </Link>
                          <Link
                              to={`/matches/${match.id}/place-verification`}
                              className="flex-1 flex items-center justify-center gap-2 py-2.5 bg-[#d84315] text-white rounded-lg text-sm font-semibold hover:bg-[#bf360c] transition-colors"
                          >
                            <MapPin size={16} />
                            장소 인증 시작
                          </Link>
                        </>
                    )}
                    {match.status === 'COMPLETED' && (
                        <button className="flex-1 py-2.5 bg-[#f5f5f5] text-[#9e9e9e] rounded-lg text-sm font-semibold cursor-not-allowed">
                          매칭 취소
                        </button>
                    )}
                    {match.status === 'CANCELED' && (
                        <div className="flex-1 text-center text-sm text-[#9e9e9e]">
                          취소됨: 예치금 {match.points.toLocaleString()}P 반환
                        </div>
                    )}
                  </div>
                </div>
            );
          })}
        </div>
      </div>
  );
}
