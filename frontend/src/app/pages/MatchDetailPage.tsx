import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import {
  AlertCircle,
  ArrowLeft,
  CalendarClock,
  CheckCircle2,
  CircleDollarSign,
  Loader2,
  MapPin,
  MessageCircle,
  UserRound,
} from 'lucide-react';
import { getMatchDetail, GetMatchResponse, MatchStatus } from '../../api/matchApi';
import { getUserMe } from '../../api/userApi';

const statusPresentation: Record<MatchStatus, {
  label: string;
  description: string;
  className: string;
}> = {
  MATCHED: {
    label: '진행 중',
    description: '매칭이 확정되어 만남을 준비하고 있습니다.',
    className: 'bg-[#fff3e0] text-[#ef6c00]',
  },
  COMPLETED: {
    label: '만남 완료',
    description: '만남이 정상적으로 완료되었습니다.',
    className: 'bg-[#e8f5e9] text-[#2e7d32]',
  },
  CANCELLED: {
    label: '취소됨',
    description: '취소 처리된 매칭입니다.',
    className: 'bg-[#f5f5f5] text-[#757575]',
  },
  AUTHOR_NO_SHOW: {
    label: '등록자 노쇼 확정',
    description: '게시글 등록자의 노쇼가 확정된 매칭입니다.',
    className: 'bg-[#ffebee] text-[#c62828]',
  },
  APPLICANT_NO_SHOW: {
    label: '신청자 노쇼 확정',
    description: '매칭 신청자의 노쇼가 확정된 매칭입니다.',
    className: 'bg-[#ffebee] text-[#c62828]',
  },
  BOTH_NO_SHOW: {
    label: '양측 노쇼 확정',
    description: '등록자와 신청자 모두의 노쇼가 확정된 매칭입니다.',
    className: 'bg-[#ffebee] text-[#c62828]',
  },
  DISPUTED: {
    label: '이의제기 처리 중',
    description: '노쇼 판정에 대한 이의제기가 접수되어 처리 중입니다.',
    className: 'bg-[#fff8e1] text-[#f57f17]',
  },
};

const formatDateTime = (value: string | null) => {
  if (!value) return '-';

  return new Date(value).toLocaleString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
};

export default function MatchDetailPage() {
  const { id } = useParams();
  const matchId = Number(id);
  const [match, setMatch] = useState<GetMatchResponse | null>(null);
  const [currentUserId, setCurrentUserId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const fetchMatch = async () => {
      if (!Number.isInteger(matchId) || matchId <= 0) {
        setError('유효하지 않은 매칭입니다.');
        setLoading(false);
        return;
      }

      setLoading(true);
      setError('');

      try {
        const [matchRes, userRes] = await Promise.all([
          getMatchDetail(matchId),
          getUserMe(),
        ]);
        setMatch(matchRes.data.data);
        setCurrentUserId(userRes.data.data.userId);
      } catch (err: any) {
        setError(err.response?.data?.message || '매칭 상세 정보를 불러오지 못했습니다.');
      } finally {
        setLoading(false);
      }
    };

    fetchMatch();
  }, [matchId]);

  if (loading) {
    return (
        <div className="flex min-h-[50vh] items-center justify-center">
          <Loader2 className="animate-spin text-[#d84315]" size={32} />
        </div>
    );
  }

  if (error || !match) {
    return (
        <div className="mx-auto max-w-xl rounded-2xl border border-[#ffcdd2] bg-white p-8 text-center">
          <AlertCircle className="mx-auto mb-3 text-[#c62828]" size={36} />
          <p className="font-semibold text-[#c62828]">{error || '매칭 정보를 찾을 수 없습니다.'}</p>
          <Link to="/matches" className="mt-5 inline-flex text-sm font-semibold text-[#d84315]">
            내 매칭으로 돌아가기
          </Link>
        </div>
    );
  }

  const status = statusPresentation[match.status];
  const isAuthor = currentUserId === match.authorId;
  const myDeposit = isAuthor ? match.authorDeposit : match.applicantDeposit;
  const postAuthor = {
    nickname: match.authorNickname,
    major: match.authorMajor,
    studentNumber: match.authorStudentNumber,
  };
  const isNoShow = ['AUTHOR_NO_SHOW', 'APPLICANT_NO_SHOW', 'BOTH_NO_SHOW'].includes(match.status);

  return (
      <div className="mx-auto max-w-3xl">
        <Link
            to="/matches"
            className="mb-5 inline-flex items-center gap-1 text-sm font-semibold text-[#616161] hover:text-[#d84315]"
        >
          <ArrowLeft size={17} />
          내 매칭
        </Link>

        <section className="overflow-hidden rounded-2xl border border-[#e0e0e0] bg-white shadow-sm">
          <div className="border-b border-[#eeeeee] px-5 py-6 sm:px-7">
            <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
              <div>
                <p className="mb-1 text-xs font-semibold text-[#9e9e9e]">매칭 #{match.matchId}</p>
                <h1 className="text-2xl font-bold text-[#212121]">{match.placeName} 만남</h1>
              </div>
              <span className={`rounded-full px-3 py-1.5 text-xs font-bold ${status.className}`}>
                {status.label}
              </span>
            </div>

            <div className={`rounded-xl px-4 py-3 text-sm ${
                isNoShow ? 'bg-[#fff5f5] text-[#b71c1c]' : 'bg-[#fafafa] text-[#616161]'
            }`}>
              {status.description}
            </div>
          </div>

          <div className="grid gap-5 px-5 py-6 sm:grid-cols-2 sm:px-7">
            <DetailItem icon={CalendarClock} label="만남 시간" value={formatDateTime(match.meetAt)} />
            <DetailItem icon={MapPin} label="만남 장소" value={match.placeName} />
            <DetailItem
                icon={UserRound}
                label="게시글 등록자"
                value={`${postAuthor.nickname} · ${postAuthor.major} · ${postAuthor.studentNumber}`}
            />
            <DetailItem
                icon={CircleDollarSign}
                label="내 책임비"
                value={`${myDeposit.toLocaleString()}P`}
            />
            <DetailItem icon={CheckCircle2} label="매칭된 시각" value={formatDateTime(match.matchedAt)} />
            <DetailItem
                icon={CheckCircle2}
                label="완료된 시각"
                value={formatDateTime(match.completedAt)}
            />
          </div>

          <div className="flex flex-wrap gap-2 border-t border-[#eeeeee] px-5 py-5 sm:px-7">
            <Link
                to={`/posts/${match.postId}`}
                className="inline-flex flex-1 items-center justify-center rounded-lg border border-[#e0e0e0] px-4 py-3 text-sm font-semibold text-[#616161] hover:border-[#d84315] hover:text-[#d84315]"
            >
              게시글 보기
            </Link>

            {match.chatRoomId && (
                <Link
                    to={`/chat/${match.chatRoomId}`}
                    state={{ matchId: match.matchId }}
                    className="inline-flex flex-1 items-center justify-center gap-2 rounded-lg border border-[#e0e0e0] px-4 py-3 text-sm font-semibold text-[#616161] hover:border-[#d84315] hover:text-[#d84315]"
                >
                  <MessageCircle size={16} />
                  채팅 보기
                </Link>
            )}

            {match.status === 'MATCHED' && (
                <Link
                    to={`/matches/${match.matchId}/place-verification`}
                    className="inline-flex flex-1 items-center justify-center gap-2 rounded-lg bg-[#d84315] px-4 py-3 text-sm font-semibold text-white hover:bg-[#bf360c]"
                >
                  <MapPin size={16} />
                  장소 인증
                </Link>
            )}

            {(isNoShow || match.status === 'DISPUTED') && (
                <Link
                    to={`/me/inquiries?view=noShow&matchId=${match.matchId}`}
                    className="inline-flex flex-1 items-center justify-center rounded-lg bg-[#d84315] px-4 py-3 text-sm font-semibold text-white hover:bg-[#bf360c]"
                >
                  {isNoShow ? '노쇼 이의제기' : '이의제기 확인'}
                </Link>
            )}
          </div>
        </section>
      </div>
  );
}

function DetailItem({
  icon: Icon,
  label,
  value,
}: {
  icon: typeof MapPin;
  label: string;
  value: string;
}) {
  return (
      <div className="flex gap-3">
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-[#fff3e0]">
          <Icon size={17} className="text-[#d84315]" />
        </div>
        <div className="min-w-0">
          <p className="text-xs font-semibold text-[#9e9e9e]">{label}</p>
          <p className="mt-1 break-words text-sm font-semibold text-[#424242]">{value}</p>
        </div>
      </div>
  );
}
