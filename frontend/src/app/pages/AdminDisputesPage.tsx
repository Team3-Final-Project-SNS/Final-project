import { useEffect, useState } from 'react';
import { Loader2, MessageSquareText, Send } from 'lucide-react';
import { useSearchParams } from 'react-router';
import {
  AdminDisputeDetail,
  AdminDisputeItem,
  DisputeStatus,
  getAdminDispute,
  getAdminDisputes,
  judgeAdminDispute,
} from '../../api/adminDisputeApi';

const filters: ('ALL' | DisputeStatus)[] = [
  'ALL',
  'SUBMITTED',
  'UNDER_REVIEW',
  'HOLD',
  'ACCEPTED',
  'PARTIALLY_ACCEPTED',
  'REJECTED',
];

const statusLabels: Record<DisputeStatus, string> = {
  SUBMITTED: '검토 대기',
  UNDER_REVIEW: '검토 중',
  ACCEPTED: '수용',
  PARTIALLY_ACCEPTED: '부분 수용',
  REJECTED: '기각',
  HOLD: '보류',
};

export default function AdminDisputesPage() {
  const [searchParams] = useSearchParams();
  const requestedDisputeId = Number(searchParams.get('disputeId'));
  const [items, setItems] = useState<AdminDisputeItem[]>([]);
  const [selected, setSelected] = useState<AdminDisputeDetail | null>(null);
  const [filter, setFilter] = useState<'ALL' | DisputeStatus>('SUBMITTED');
  const [judgment, setJudgment] = useState<'ACCEPTED' | 'PARTIALLY_ACCEPTED' | 'REJECTED' | 'HOLD'>('ACCEPTED');
  const [comment, setComment] = useState('');
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState('');

  const loadItems = async () => {
    setLoading(true);
    setMessage('');
    try {
      const response = await getAdminDisputes(filter === 'ALL' ? undefined : filter);
      setItems(response.data.data.content);
    } catch (err: any) {
      setMessage(err.response?.data?.message || '이의제기 목록을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const openDetail = async (disputeId: number) => {
    setDetailLoading(true);
    setMessage('');
    try {
      const response = await getAdminDispute(disputeId);
      setSelected(response.data.data);
      setComment('');
    } catch (err: any) {
      setMessage(err.response?.data?.message || '이의제기 상세를 불러오지 못했습니다.');
    } finally {
      setDetailLoading(false);
    }
  };

  useEffect(() => {
    loadItems();
  }, [filter]);

  useEffect(() => {
    if (Number.isInteger(requestedDisputeId) && requestedDisputeId > 0) {
      openDetail(requestedDisputeId);
    }
  }, [requestedDisputeId]);

  const handleJudge = async () => {
    if (!selected || !comment.trim()) {
      setMessage('판정 내용을 입력해주세요.');
      return;
    }

    setSubmitting(true);
    setMessage('');
    try {
      await judgeAdminDispute(selected.disputeId, judgment, comment.trim());
      setMessage('이의제기 판정이 완료되었습니다.');
      await openDetail(selected.disputeId);
      await loadItems();
    } catch (err: any) {
      setMessage(err.response?.data?.message || '이의제기 판정에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  const canJudge = selected && !['ACCEPTED', 'PARTIALLY_ACCEPTED', 'REJECTED'].includes(selected.status);

  return (
    <div className="min-h-screen bg-gradient-to-br from-[#fff7ed] via-[#f7fbff] to-[#eaf7f1]">
      <main className="mx-auto max-w-screen-xl px-4 py-10">
        <h1 className="text-3xl font-bold text-[#212121]">이의제기 관리</h1>
        <p className="mb-6 mt-2 text-sm text-[#757575]">이의제기 사유와 인증·채팅 내역을 확인하고 판정합니다.</p>

        {message && (
          <div className="mb-5 rounded-lg border border-[#e0e0e0] bg-white px-4 py-3 text-sm font-semibold text-[#616161]">
            {message}
          </div>
        )}

        <div className="mb-6 flex gap-2 overflow-x-auto pb-1">
          {filters.map((item) => (
            <button
              key={item}
              type="button"
              onClick={() => setFilter(item)}
              className={`shrink-0 rounded-full px-4 py-2 text-sm font-semibold ${
                filter === item ? 'bg-[#d84315] text-white' : 'border border-[#e0e0e0] bg-white text-[#616161]'
              }`}
            >
              {item === 'ALL' ? '전체' : statusLabels[item]}
            </button>
          ))}
        </div>

        <div className="grid gap-5 lg:grid-cols-[360px_1fr]">
          <section className="rounded-2xl border border-[#e0e0e0] bg-white p-5 shadow-sm">
            <h2 className="mb-4 text-lg font-bold">이의제기 목록</h2>
            {loading ? (
              <div className="py-12 text-center text-sm text-[#9e9e9e]">
                <Loader2 className="mx-auto mb-3 animate-spin text-[#d84315]" />
                목록을 불러오는 중...
              </div>
            ) : items.length ? (
              <div className="space-y-2">
                {items.map((item) => (
                  <button
                    key={item.disputeId}
                    type="button"
                    onClick={() => openDetail(item.disputeId)}
                    className={`w-full rounded-xl border p-4 text-left ${
                      selected?.disputeId === item.disputeId
                        ? 'border-[#d84315] bg-[#fff8f5]'
                        : 'border-[#eeeeee] hover:border-[#d84315]'
                    }`}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="rounded bg-[#fff3e0] px-2 py-1 text-xs font-bold text-[#e65100]">
                        {statusLabels[item.status]}
                      </span>
                      <span className="text-xs text-[#9e9e9e]">#{item.disputeId}</span>
                    </div>
                    <p className="mt-3 font-bold">{item.applicantNickname}</p>
                    <p className="mt-1 line-clamp-2 text-xs leading-5 text-[#757575]">{item.reason}</p>
                  </button>
                ))}
              </div>
            ) : (
              <div className="rounded-xl border border-dashed border-[#e0e0e0] p-8 text-center text-sm text-[#9e9e9e]">
                표시할 이의제기가 없습니다.
              </div>
            )}
          </section>

          <section className="rounded-2xl border border-[#e0e0e0] bg-white p-6 shadow-sm">
            <h2 className="mb-5 text-xl font-bold">이의제기 상세</h2>
            {detailLoading ? (
              <div className="py-16 text-center text-sm text-[#9e9e9e]">
                <Loader2 className="mx-auto mb-3 animate-spin text-[#d84315]" />
                상세 내용을 불러오는 중...
              </div>
            ) : selected ? (
              <div>
                <div className="space-y-2 rounded-xl bg-[#fafafa] p-4">
                  <InfoRow label="신청자" value={selected.applicantNickname} />
                  <InfoRow label="관련 매칭" value={`#${selected.matchId}`} />
                  <InfoRow label="이의제기 유형" value={selected.disputeType} />
                  <InfoRow label="처리 상태" value={statusLabels[selected.status]} />
                  <InfoRow label="인증 상태" value={selected.verificationStatus} />
                  <InfoRow label="등록자 인증" value={formatOptionalDate(selected.authorPlaceVerifiedAt)} />
                  <InfoRow label="신청자 인증" value={formatOptionalDate(selected.applicantPlaceVerifiedAt)} />
                </div>

                <DetailSection title="제출 사유">{selected.reason}</DetailSection>

                <div className="mt-5">
                  <h3 className="mb-3 text-sm font-bold text-[#616161]">관련 채팅 내역</h3>
                  <div className="max-h-60 space-y-2 overflow-y-auto rounded-xl border border-[#eeeeee] p-4">
                    {selected.chatMessages.length ? selected.chatMessages.map((chat, index) => (
                      <div key={`${chat.senderId}-${chat.createdAt}-${index}`} className="rounded-lg bg-[#fafafa] p-3">
                        <div className="flex justify-between gap-3 text-xs text-[#9e9e9e]">
                          <strong className="text-[#616161]">{chat.senderNickname}</strong>
                          <span>{formatDateTime(chat.createdAt)}</span>
                        </div>
                        <p className="mt-1 whitespace-pre-wrap text-sm text-[#424242]">{chat.content}</p>
                      </div>
                    )) : (
                      <div className="flex items-center justify-center gap-2 py-6 text-sm text-[#9e9e9e]">
                        <MessageSquareText size={17} />
                        확인할 채팅 내역이 없습니다.
                      </div>
                    )}
                  </div>
                </div>

                {canJudge && (
                  <div className="mt-6 border-t border-[#eeeeee] pt-5">
                    <h3 className="mb-3 text-sm font-bold text-[#616161]">판정 처리</h3>
                    <select
                      value={judgment}
                      onChange={(event) => setJudgment(event.target.value as typeof judgment)}
                      className="mb-3 h-11 w-full rounded-lg border border-[#e0e0e0] bg-white px-3 text-sm"
                    >
                      <option value="ACCEPTED">수용</option>
                      <option value="PARTIALLY_ACCEPTED">부분 수용</option>
                      <option value="REJECTED">기각</option>
                      <option value="HOLD">보류 및 추가 증빙 요청</option>
                    </select>
                    <textarea
                      value={comment}
                      onChange={(event) => setComment(event.target.value)}
                      rows={5}
                      maxLength={1000}
                      placeholder="판정 내용 또는 추가 증빙 요청 내용을 입력하세요."
                      className="w-full resize-none rounded-lg border border-[#e0e0e0] p-3 text-sm focus:border-[#d84315] focus:outline-none"
                    />
                    <div className="mt-3 flex justify-end">
                      <button
                        type="button"
                        disabled={submitting}
                        onClick={handleJudge}
                        className="inline-flex items-center gap-2 rounded-lg bg-[#d84315] px-5 py-3 text-sm font-bold text-white disabled:opacity-60"
                      >
                        {submitting ? <Loader2 className="animate-spin" size={16} /> : <Send size={16} />}
                        판정 등록
                      </button>
                    </div>
                  </div>
                )}
              </div>
            ) : (
              <div className="rounded-xl border border-dashed border-[#e0e0e0] p-12 text-center text-sm text-[#9e9e9e]">
                이의제기를 선택하면 상세 내용이 표시됩니다.
              </div>
            )}
          </section>
        </div>
      </main>
    </div>
  );
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex min-h-10 items-center">
      <span className="w-32 shrink-0 border-r border-[#dddddd] pr-4 text-sm font-bold text-[#757575]">{label}</span>
      <span className="min-w-0 pl-4 text-sm font-semibold text-[#212121]">{value}</span>
    </div>
  );
}

function DetailSection({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="mt-5 rounded-xl border border-[#eeeeee] p-4">
      <h3 className="mb-2 text-sm font-bold text-[#616161]">{title}</h3>
      <p className="whitespace-pre-wrap text-sm leading-6 text-[#424242]">{children}</p>
    </div>
  );
}

function formatOptionalDate(value: string | null) {
  return value ? formatDateTime(value) : '미인증';
}

function formatDateTime(value: string) {
  return new Date(value).toLocaleString('ko-KR', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}
