import { FormEvent, useEffect, useState } from 'react';
import { AlertCircle, ArrowLeft, Flag, Loader2, Send, Trash2 } from 'lucide-react';
import { Link, useSearchParams } from 'react-router';
import {
  createReport,
  deleteReport,
  getMyReports,
  MyReportItem,
  ReportReason,
  ReportStatus,
} from '../../api/reportApi';

const reportReasons: { value: ReportReason; label: string }[] = [
  { value: 'SPAM', label: '스팸/홍보' },
  { value: 'OBSCENE', label: '음란/부적절한 내용' },
  { value: 'FRAUD', label: '사기/허위 정보' },
  { value: 'ABUSE', label: '욕설/비방' },
  { value: 'OTHER', label: '기타' },
];

const statusLabels: Record<ReportStatus, string> = {
  PENDING: '접수됨',
  ACCEPTED: '채택',
  REJECTED: '기각',
  WITHDRAWN: '취소됨',
};

const statusClasses: Record<ReportStatus, string> = {
  PENDING: 'bg-[#fff3e0] text-[#ef6c00]',
  ACCEPTED: 'bg-[#e8f5e9] text-[#2e7d32]',
  REJECTED: 'bg-[#ffebee] text-[#c62828]',
  WITHDRAWN: 'bg-[#f5f5f5] text-[#757575]',
};

export default function ReportCenterPage() {
  const [searchParams] = useSearchParams();
  const initialTargetId = searchParams.get('targetId') || '';
  const [reports, setReports] = useState<MyReportItem[]>([]);
  const [targetId, setTargetId] = useState(initialTargetId);
  const [reason, setReason] = useState<ReportReason>('OTHER');
  const [detail, setDetail] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const loadReports = async () => {
    setLoading(true);
    setError('');
    try {
      const res = await getMyReports(page, 10);
      setReports(res.data.data.content);
      setTotalPages(res.data.data.totalPages || 1);
    } catch (err) {
      console.error('Failed to load reports', err);
      setError('신고 내역을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadReports();
  }, [page]);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const numericTargetId = Number(targetId);

    if (!numericTargetId || numericTargetId <= 0) {
      setError('신고할 게시글 ID를 입력해주세요.');
      return;
    }

    setSubmitting(true);
    setError('');
    setSuccess('');
    try {
      await createReport({
        targetId: numericTargetId,
        reason,
        detail: detail.trim() || undefined,
      });
      setSuccess('신고가 접수되었습니다.');
      setDetail('');
      setReason('OTHER');
      if (!initialTargetId) {
        setTargetId('');
      }
      setPage(0);
      await loadReports();
    } catch (err: any) {
      setError(err.response?.data?.message || '신고 접수에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (reportId: number) => {
    if (!confirm('접수된 신고를 취소하시겠습니까?')) {
      return;
    }

    setError('');
    setSuccess('');
    try {
      await deleteReport(reportId);
      setSuccess('신고가 취소되었습니다.');
      await loadReports();
    } catch (err: any) {
      setError(err.response?.data?.message || '신고 취소에 실패했습니다.');
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
        <h1 className="text-3xl font-bold text-[#212121]">신고센터</h1>
        <p className="mt-2 text-sm text-[#757575]">부적절한 게시글을 신고하고 처리 상태를 확인할 수 있습니다.</p>
      </div>

      {error && <Notice tone="error" message={error} />}
      {success && <Notice tone="success" message={success} />}

      <div className="grid gap-6 lg:grid-cols-[360px_1fr]">
        <section className="rounded-2xl border border-[#e0e0e0] bg-white p-5 shadow-sm">
          <h2 className="mb-4 text-lg font-bold text-[#212121]">신고 접수</h2>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="mb-1 block text-xs font-bold text-[#757575]">게시글 ID</label>
              <input
                value={targetId}
                onChange={(event) => setTargetId(event.target.value.replace(/\D/g, ''))}
                className="w-full rounded-lg border border-[#e0e0e0] px-3 py-2 text-sm focus:border-[#d84315] focus:outline-none focus:ring-2 focus:ring-[#fff3e0]"
                placeholder="신고 대상 게시글 ID"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-bold text-[#757575]">신고 사유</label>
              <select
                value={reason}
                onChange={(event) => setReason(event.target.value as ReportReason)}
                className="w-full rounded-lg border border-[#e0e0e0] bg-white px-3 py-2 text-sm focus:border-[#d84315] focus:outline-none focus:ring-2 focus:ring-[#fff3e0]"
              >
                {reportReasons.map((item) => (
                  <option key={item.value} value={item.value}>
                    {item.label}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-bold text-[#757575]">상세 내용</label>
              <textarea
                value={detail}
                onChange={(event) => setDetail(event.target.value)}
                maxLength={500}
                rows={7}
                className="w-full resize-none rounded-lg border border-[#e0e0e0] px-3 py-2 text-sm focus:border-[#d84315] focus:outline-none focus:ring-2 focus:ring-[#fff3e0]"
                placeholder="신고 사유를 자세히 입력해주세요"
              />
              <p className="mt-1 text-right text-xs text-[#9e9e9e]">{detail.length}/500</p>
            </div>
            <button
              type="submit"
              disabled={submitting}
              className="flex w-full items-center justify-center gap-2 rounded-lg bg-[#d84315] px-4 py-3 text-sm font-bold text-white shadow-md transition-colors hover:bg-[#bf360c] disabled:opacity-60"
            >
              {submitting ? <Loader2 size={18} className="animate-spin" /> : <Send size={18} />}
              신고 접수
            </button>
          </form>
        </section>

        <section className="rounded-2xl border border-[#e0e0e0] bg-white p-5 shadow-sm">
          <h2 className="mb-4 text-lg font-bold text-[#212121]">내 신고 내역</h2>
          {loading ? (
            <div className="py-12 text-center text-sm text-[#9e9e9e]">신고 내역을 불러오는 중...</div>
          ) : reports.length > 0 ? (
            <div className="space-y-3">
              {reports.map((report) => (
                <div key={report.reportId} className="rounded-xl border border-[#eeeeee] p-4">
                  <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <span className={`rounded px-2.5 py-1 text-xs font-bold ${statusClasses[report.status]}`}>
                        {statusLabels[report.status]}
                      </span>
                      <span className="text-xs font-semibold text-[#757575]">{reasonLabel(report.reason)}</span>
                    </div>
                    <span className="text-xs text-[#9e9e9e]">{formatDateTime(report.createdAt)}</span>
                  </div>
                  <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                    <div>
                      <p className="font-bold text-[#212121]">게시글 #{report.targetId}</p>
                      <Link to={`/posts/${report.targetId}`} className="mt-1 inline-block text-sm font-semibold text-[#d84315]">
                        게시글 확인
                      </Link>
                    </div>
                    {report.status === 'PENDING' && (
                      <button
                        type="button"
                        onClick={() => handleDelete(report.reportId)}
                        className="inline-flex items-center justify-center gap-2 rounded-lg border border-red-200 px-4 py-2.5 text-sm font-bold text-red-500 transition-colors hover:bg-red-50"
                      >
                        <Trash2 size={16} />
                        신고 취소
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-[#e0e0e0] p-12 text-center text-sm text-[#9e9e9e]">
              <Flag className="mb-3 text-[#d84315]" size={32} />
              접수한 신고가 없습니다.
            </div>
          )}

          {totalPages > 1 && (
            <div className="mt-6 flex items-center justify-center gap-2">
              <button
                type="button"
                disabled={page === 0}
                onClick={() => setPage((prev) => prev - 1)}
                className="rounded border border-[#e0e0e0] px-3 py-1.5 text-sm hover:bg-[#f5f5f5] disabled:opacity-50"
              >
                &lt;
              </button>
              {[...Array(totalPages)].map((_, index) => (
                <button
                  key={index}
                  type="button"
                  onClick={() => setPage(index)}
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
                onClick={() => setPage((prev) => prev + 1)}
                className="rounded border border-[#e0e0e0] px-3 py-1.5 text-sm hover:bg-[#f5f5f5] disabled:opacity-50"
              >
                &gt;
              </button>
            </div>
          )}
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

function reasonLabel(reason: ReportReason) {
  return reportReasons.find((item) => item.value === reason)?.label || reason;
}

function formatDateTime(value: string) {
  return new Date(value).toLocaleString('ko-KR', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}
