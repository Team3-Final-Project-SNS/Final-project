import { FormEvent, useEffect, useState } from 'react';
import { AlertCircle, ArrowLeft, Loader2, MessageSquare, Send, Trash2 } from 'lucide-react';
import { Link } from 'react-router';
import {
  cancelInquiry,
  createInquiry,
  getInquiry,
  getMyInquiries,
  InquiryAnswerStatus,
  InquiryDetail,
  InquiryListItem,
  InquiryType,
} from '../../api/inquiryApi';

const inquiryTypes: { value: InquiryType; label: string }[] = [
  { value: 'ACCOUNT', label: '계정' },
  { value: 'PAYMENT', label: '결제' },
  { value: 'MATCH', label: '매칭' },
  { value: 'REPORT', label: '신고' },
  { value: 'USAGE', label: '이용 방법' },
  { value: 'HISTORY', label: '이용 내역' },
  { value: 'OTHER', label: '기타' },
];

const statusLabels: Record<InquiryAnswerStatus, string> = {
  PENDING: '접수됨',
  READ: '확인 중',
  ANSWERED: '답변 완료',
  WITHDRAWN: '취소됨',
};

const statusClasses: Record<InquiryAnswerStatus, string> = {
  PENDING: 'bg-[#fff3e0] text-[#ef6c00]',
  READ: 'bg-[#e3f2fd] text-[#1565c0]',
  ANSWERED: 'bg-[#e8f5e9] text-[#2e7d32]',
  WITHDRAWN: 'bg-[#f5f5f5] text-[#757575]',
};

export default function InquiryCenterPage() {
  const [items, setItems] = useState<InquiryListItem[]>([]);
  const [selected, setSelected] = useState<InquiryDetail | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [type, setType] = useState<InquiryType>('OTHER');

  const loadInquiries = async (nextPage = page) => {
    setLoading(true);
    setError('');
    try {
      const res = await getMyInquiries(nextPage, 10);
      setItems(res.data.data.content);
      setTotalPages(res.data.data.totalPages || 1);
    } catch (err) {
      console.error('Failed to load inquiries', err);
      setError('문의 내역을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadInquiries(page);
  }, [page]);

  const handleSelect = async (inquiryId: number) => {
    setDetailLoading(true);
    setError('');
    try {
      const res = await getInquiry(inquiryId);
      setSelected(res.data.data);
    } catch (err) {
      console.error('Failed to load inquiry detail', err);
      setError('문의 상세 내용을 불러오지 못했습니다.');
    } finally {
      setDetailLoading(false);
    }
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!title.trim() || !content.trim()) {
      setError('제목과 내용을 모두 입력해주세요.');
      return;
    }

    setSubmitting(true);
    setError('');
    setSuccess('');
    try {
      const res = await createInquiry({ title: title.trim(), content: content.trim(), type });
      setTitle('');
      setContent('');
      setType('OTHER');
      setSuccess('문의가 접수되었습니다.');
      await loadInquiries(0);
      setPage(0);
      await handleSelect(res.data.data.inquiryId);
    } catch (err: any) {
      setError(err.response?.data?.message || '문의 접수에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleCancel = async (inquiryId: number) => {
    if (!confirm('접수된 문의를 취소하시겠습니까?')) {
      return;
    }

    setError('');
    setSuccess('');
    try {
      await cancelInquiry(inquiryId);
      setSuccess('문의가 취소되었습니다.');
      setSelected(null);
      await loadInquiries(page);
    } catch (err: any) {
      setError(err.response?.data?.message || '문의 취소에 실패했습니다.');
    }
  };

  return (
    <div className="mx-auto max-w-5xl">
      <div className="mb-6">
        <Link
          to="/me"
          className="mb-3 inline-flex items-center gap-1 text-sm font-semibold text-[#616161] transition-colors hover:text-[#d84315]"
        >
          <ArrowLeft size={16} />
          내 정보
        </Link>
        <h1 className="text-3xl font-bold text-[#212121]">고객센터</h1>
        <p className="mt-2 text-sm text-[#757575]">1:1 문의를 접수하고 답변 상태를 확인할 수 있습니다.</p>
      </div>

      {error && <Notice tone="error" message={error} />}
      {success && <Notice tone="success" message={success} />}

      <div className="grid gap-6 lg:grid-cols-[360px_1fr]">
        <section className="rounded-2xl border border-[#e0e0e0] bg-white p-5 shadow-sm">
          <h2 className="mb-4 text-lg font-bold text-[#212121]">문의 접수</h2>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="mb-1 block text-xs font-bold text-[#757575]">문의 유형</label>
              <select
                value={type}
                onChange={(event) => setType(event.target.value as InquiryType)}
                className="w-full rounded-lg border border-[#e0e0e0] bg-white px-3 py-2 text-sm focus:border-[#d84315] focus:outline-none focus:ring-2 focus:ring-[#fff3e0]"
              >
                {inquiryTypes.map((item) => (
                  <option key={item.value} value={item.value}>
                    {item.label}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-bold text-[#757575]">제목</label>
              <input
                value={title}
                onChange={(event) => setTitle(event.target.value)}
                maxLength={200}
                className="w-full rounded-lg border border-[#e0e0e0] px-3 py-2 text-sm focus:border-[#d84315] focus:outline-none focus:ring-2 focus:ring-[#fff3e0]"
                placeholder="문의 제목을 입력하세요"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-bold text-[#757575]">내용</label>
              <textarea
                value={content}
                onChange={(event) => setContent(event.target.value)}
                rows={7}
                className="w-full resize-none rounded-lg border border-[#e0e0e0] px-3 py-2 text-sm focus:border-[#d84315] focus:outline-none focus:ring-2 focus:ring-[#fff3e0]"
                placeholder="문의 내용을 자세히 입력해주세요"
              />
            </div>
            <button
              type="submit"
              disabled={submitting}
              className="flex w-full items-center justify-center gap-2 rounded-lg bg-[#d84315] px-4 py-3 text-sm font-bold text-white shadow-md transition-colors hover:bg-[#bf360c] disabled:opacity-60"
            >
              {submitting ? <Loader2 size={18} className="animate-spin" /> : <Send size={18} />}
              문의 접수
            </button>
          </form>
        </section>

        <section className="space-y-4">
          <div className="rounded-2xl border border-[#e0e0e0] bg-white p-5 shadow-sm">
            <h2 className="mb-4 text-lg font-bold text-[#212121]">내 문의 내역</h2>
            {loading ? (
              <div className="py-10 text-center text-sm text-[#9e9e9e]">문의 내역을 불러오는 중...</div>
            ) : items.length > 0 ? (
              <div className="space-y-2">
                {items.map((item) => (
                  <button
                    key={item.inquiryId}
                    type="button"
                    onClick={() => handleSelect(item.inquiryId)}
                    className="w-full rounded-xl border border-[#eeeeee] p-4 text-left transition-colors hover:border-[#d84315] hover:bg-[#fffaf7]"
                  >
                    <div className="mb-2 flex items-center justify-between gap-3">
                      <span className={`rounded px-2.5 py-1 text-xs font-bold ${statusClasses[item.answerStatus]}`}>
                        {statusLabels[item.answerStatus]}
                      </span>
                      <span className="text-xs text-[#9e9e9e]">{formatDateTime(item.createdAt)}</span>
                    </div>
                    <p className="font-bold text-[#212121]">{item.title}</p>
                    <p className="mt-1 text-xs font-semibold text-[#757575]">{typeLabel(item.type)}</p>
                  </button>
                ))}
              </div>
            ) : (
              <div className="rounded-xl border border-dashed border-[#e0e0e0] p-8 text-center text-sm text-[#9e9e9e]">
                접수한 문의가 없습니다.
              </div>
            )}
          </div>

          {totalPages > 1 && (
            <Pagination page={page} totalPages={totalPages} onChange={setPage} />
          )}

          <div className="rounded-2xl border border-[#e0e0e0] bg-white p-5 shadow-sm">
            <h2 className="mb-4 text-lg font-bold text-[#212121]">문의 상세</h2>
            {detailLoading ? (
              <div className="py-10 text-center text-sm text-[#9e9e9e]">상세 내용을 불러오는 중...</div>
            ) : selected ? (
              <div className="space-y-4">
                <div>
                  <div className="mb-2 flex flex-wrap items-center gap-2">
                    <span className={`rounded px-2.5 py-1 text-xs font-bold ${statusClasses[selected.answerStatus]}`}>
                      {statusLabels[selected.answerStatus]}
                    </span>
                    <span className="text-xs font-semibold text-[#757575]">{typeLabel(selected.type)}</span>
                  </div>
                  <h3 className="text-xl font-bold text-[#212121]">{selected.title}</h3>
                  <p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-[#616161]">{selected.content}</p>
                </div>

                {selected.answer ? (
                  <div className="rounded-xl bg-[#f8fbf8] p-4">
                    <p className="mb-2 text-sm font-bold text-[#2e7d32]">{selected.answer.adminName} 답변</p>
                    <p className="whitespace-pre-wrap text-sm leading-6 text-[#424242]">{selected.answer.content}</p>
                    <p className="mt-3 text-xs text-[#9e9e9e]">{formatDateTime(selected.answer.createdAt)}</p>
                  </div>
                ) : (
                  <div className="rounded-xl bg-[#fafafa] p-4 text-sm text-[#757575]">
                    아직 답변이 등록되지 않았습니다.
                  </div>
                )}

                {selected.answerStatus === 'PENDING' && (
                  <button
                    type="button"
                    onClick={() => handleCancel(selected.inquiryId)}
                    className="inline-flex items-center gap-2 rounded-lg border border-red-200 px-4 py-2.5 text-sm font-bold text-red-500 transition-colors hover:bg-red-50"
                  >
                    <Trash2 size={16} />
                    문의 취소
                  </button>
                )}
              </div>
            ) : (
              <div className="flex flex-col items-center justify-center py-12 text-center text-sm text-[#9e9e9e]">
                <MessageSquare className="mb-3 text-[#d84315]" size={32} />
                문의를 선택하면 상세 내용과 답변을 볼 수 있습니다.
              </div>
            )}
          </div>
        </section>
      </div>
    </div>
  );
}

function Notice({ tone, message }: { tone: 'error' | 'success'; message: string }) {
  const isError = tone === 'error';
  return (
    <div
      className={`mb-5 flex items-start gap-2 rounded-lg border px-4 py-3 text-sm ${
        isError ? 'border-[#ef5350] bg-[#ffebee] text-[#c62828]' : 'border-[#a5d6a7] bg-[#e8f5e9] text-[#2e7d32]'
      }`}
    >
      <AlertCircle size={18} className="mt-0.5 shrink-0" />
      <span>{message}</span>
    </div>
  );
}

function Pagination({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (page: number) => void }) {
  return (
    <div className="flex items-center justify-center gap-2">
      <button
        type="button"
        disabled={page === 0}
        onClick={() => onChange(page - 1)}
        className="rounded border border-[#e0e0e0] px-3 py-1.5 text-sm hover:bg-[#f5f5f5] disabled:opacity-50"
      >
        &lt;
      </button>
      {[...Array(totalPages)].map((_, index) => (
        <button
          key={index}
          type="button"
          onClick={() => onChange(index)}
          className={`rounded px-3 py-1.5 text-sm ${
            page === index ? 'bg-[#d84315] text-white' : 'border border-[#e0e0e0] hover:bg-[#f5f5f5]'
          }`}
        >
          {index + 1}
        </button>
      ))}
      <button
        type="button"
        disabled={page >= totalPages - 1}
        onClick={() => onChange(page + 1)}
        className="rounded border border-[#e0e0e0] px-3 py-1.5 text-sm hover:bg-[#f5f5f5] disabled:opacity-50"
      >
        &gt;
      </button>
    </div>
  );
}

function typeLabel(type: InquiryType) {
  return inquiryTypes.find((item) => item.value === type)?.label || type;
}

function formatDateTime(value: string) {
  return new Date(value).toLocaleString('ko-KR', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}
