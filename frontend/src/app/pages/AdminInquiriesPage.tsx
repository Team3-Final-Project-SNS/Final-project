import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { ArrowLeft, Loader2, Send } from 'lucide-react';
import {
  AdminInquiryDetail,
  AdminInquiryItem,
  answerAdminInquiry,
  getAdminInquiries,
  getAdminInquiry,
} from '../../api/adminInquiryApi';
import { InquiryAnswerStatus, InquiryType } from '../../api/inquiryApi';

const statusFilters: ('ALL' | InquiryAnswerStatus)[] = ['ALL', 'PENDING', 'READ', 'ANSWERED', 'WITHDRAWN'];
const typeFilters: ('ALL' | InquiryType)[] = ['ALL', 'ACCOUNT', 'PAYMENT', 'MATCH', 'REPORT', 'USAGE', 'HISTORY', 'OTHER'];

export default function AdminInquiriesPage() {
  const [items, setItems] = useState<AdminInquiryItem[]>([]);
  const [selected, setSelected] = useState<AdminInquiryDetail | null>(null);
  const [answer, setAnswer] = useState('');
  const [status, setStatus] = useState<'ALL' | InquiryAnswerStatus>('PENDING');
  const [type, setType] = useState<'ALL' | InquiryType>('ALL');
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState('');

  const loadInquiries = async () => {
    setLoading(true);
    setMessage('');
    try {
      const res = await getAdminInquiries(status === 'ALL' ? undefined : status, type === 'ALL' ? undefined : type, 0, 20);
      setItems(res.data.data.content);
    } catch (err: any) {
      setMessage(err.response?.data?.message || '문의 목록을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadInquiries();
  }, [status, type]);

  const handleSelect = async (inquiryId: number) => {
    setMessage('');
    try {
      const res = await getAdminInquiry(inquiryId);
      setSelected(res.data.data);
      setAnswer('');
    } catch (err: any) {
      setMessage(err.response?.data?.message || '문의 상세를 불러오지 못했습니다.');
    }
  };

  const handleAnswer = async () => {
    if (!selected || !answer.trim()) {
      setMessage('답변 내용을 입력해주세요.');
      return;
    }

    try {
      await answerAdminInquiry(selected.inquiryId, answer.trim());
      setMessage('답변이 등록되었습니다.');
      await handleSelect(selected.inquiryId);
      await loadInquiries();
    } catch (err: any) {
      setMessage(err.response?.data?.message || '답변 등록에 실패했습니다.');
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-[#fff7ed] via-[#f7fbff] to-[#eaf7f1]">
      <main className="mx-auto max-w-screen-lg px-4 py-10">
        <Link to="/admin" className="mb-4 inline-flex items-center gap-1 text-sm font-semibold text-[#616161] hover:text-[#d84315]">
          <ArrowLeft size={16} />
          관리자 콘솔
        </Link>
        <h1 className="text-3xl font-bold text-[#212121]">고객 문의 관리</h1>
        <p className="mb-6 mt-2 text-sm text-[#757575]">접수된 문의를 확인하고 답변합니다.</p>

        {message && (
          <div className="mb-5 rounded-lg border border-[#e0e0e0] bg-white px-4 py-3 text-sm font-semibold text-[#616161]">
            {message}
          </div>
        )}

        <div className="mb-4 flex flex-wrap gap-2">
          {statusFilters.map((item) => (
            <button
              key={item}
              type="button"
              onClick={() => setStatus(item)}
              className={`rounded-full px-4 py-2 text-sm font-semibold ${
                status === item ? 'bg-[#d84315] text-white' : 'border border-[#e0e0e0] bg-white text-[#616161]'
              }`}
            >
              {item === 'ALL' ? '전체 상태' : item}
            </button>
          ))}
        </div>
        <div className="mb-6 flex flex-wrap gap-2">
          {typeFilters.map((item) => (
            <button
              key={item}
              type="button"
              onClick={() => setType(item)}
              className={`rounded-full px-4 py-2 text-sm font-semibold ${
                type === item ? 'bg-[#212121] text-white' : 'border border-[#e0e0e0] bg-white text-[#616161]'
              }`}
            >
              {item === 'ALL' ? '전체 유형' : item}
            </button>
          ))}
        </div>

        <div className="grid gap-5 lg:grid-cols-[360px_1fr]">
          <section className="rounded-2xl border border-[#e0e0e0] bg-white p-5 shadow-sm">
            <h2 className="mb-4 text-lg font-bold text-[#212121]">문의 목록</h2>
            {loading ? (
              <div className="py-12 text-center text-sm text-[#9e9e9e]">
                <Loader2 className="mx-auto mb-3 animate-spin text-[#d84315]" />
                문의 목록을 불러오는 중...
              </div>
            ) : items.length > 0 ? (
              <div className="space-y-2">
                {items.map((item) => (
                  <button
                    key={item.inquiryId}
                    type="button"
                    onClick={() => handleSelect(item.inquiryId)}
                    className="w-full rounded-xl border border-[#eeeeee] p-4 text-left hover:border-[#d84315] hover:bg-[#fffaf7]"
                  >
                    <div className="mb-2 flex items-center justify-between gap-2">
                      <span className="rounded bg-[#fff3e0] px-2.5 py-1 text-xs font-bold text-[#ef6c00]">
                        {item.answerStatus}
                      </span>
                      <span className="text-xs text-[#9e9e9e]">{item.type}</span>
                    </div>
                    <p className="font-bold text-[#212121]">{item.title}</p>
                    <p className="mt-1 text-xs text-[#757575]">{item.userNickname}</p>
                  </button>
                ))}
              </div>
            ) : (
              <div className="rounded-xl border border-dashed border-[#e0e0e0] p-8 text-center text-sm text-[#9e9e9e]">
                표시할 문의가 없습니다.
              </div>
            )}
          </section>

          <section className="rounded-2xl border border-[#e0e0e0] bg-white p-5 shadow-sm">
            <h2 className="mb-4 text-lg font-bold text-[#212121]">문의 상세</h2>
            {selected ? (
              <div className="space-y-4">
                <div>
                  <p className="mb-2 text-xs font-bold text-[#9e9e9e]">
                    {selected.userNickname} · {selected.userEmail} · {selected.universityName}
                  </p>
                  <h3 className="text-xl font-bold text-[#212121]">{selected.title}</h3>
                  <p className="mt-3 whitespace-pre-wrap rounded-xl bg-[#fafafa] p-4 text-sm leading-6 text-[#424242]">
                    {selected.content}
                  </p>
                </div>

                {selected.answer ? (
                  <div className="rounded-xl bg-[#f8fbf8] p-4">
                    <p className="mb-2 text-sm font-bold text-[#2e7d32]">{selected.answer.adminName} 답변</p>
                    <p className="whitespace-pre-wrap text-sm leading-6 text-[#424242]">{selected.answer.content}</p>
                  </div>
                ) : (
                  <div className="space-y-3">
                    <textarea
                      value={answer}
                      onChange={(event) => setAnswer(event.target.value)}
                      maxLength={2000}
                      rows={7}
                      className="w-full resize-none rounded-lg border border-[#e0e0e0] px-3 py-2 text-sm focus:border-[#d84315] focus:outline-none focus:ring-2 focus:ring-[#fff3e0]"
                      placeholder="답변 내용을 입력하세요"
                    />
                    <button
                      type="button"
                      onClick={handleAnswer}
                      className="inline-flex items-center gap-2 rounded-lg bg-[#d84315] px-5 py-2.5 text-sm font-bold text-white hover:bg-[#bf360c]"
                    >
                      <Send size={16} />
                      답변 등록
                    </button>
                  </div>
                )}
              </div>
            ) : (
              <div className="rounded-xl border border-dashed border-[#e0e0e0] p-12 text-center text-sm text-[#9e9e9e]">
                문의를 선택하면 상세 내용이 표시됩니다.
              </div>
            )}
          </section>
        </div>
      </main>
    </div>
  );
}
