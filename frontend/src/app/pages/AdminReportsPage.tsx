import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { ArrowLeft, CheckCircle2, Loader2, XCircle } from 'lucide-react';
import { AdminReportItem, getAdminReports, processAdminReport } from '../../api/adminReportApi';
import { ReportStatus } from '../../api/reportApi';

const filters: ('ALL' | ReportStatus)[] = ['ALL', 'PENDING', 'ACCEPTED', 'REJECTED', 'WITHDRAWN'];

export default function AdminReportsPage() {
  const [reports, setReports] = useState<AdminReportItem[]>([]);
  const [filter, setFilter] = useState<'ALL' | ReportStatus>('PENDING');
  const [loading, setLoading] = useState(true);
  const [processingId, setProcessingId] = useState<number | null>(null);
  const [message, setMessage] = useState('');

  const loadReports = async () => {
    setLoading(true);
    setMessage('');
    try {
      const res = await getAdminReports(filter === 'ALL' ? undefined : filter, 0, 20);
      setReports(res.data.data.content);
    } catch (err: any) {
      setMessage(err.response?.data?.message || '신고 목록을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadReports();
  }, [filter]);

  const handleProcess = async (reportId: number, status: 'ACCEPTED' | 'REJECTED') => {
    const comment = prompt(status === 'ACCEPTED' ? '채택 사유를 입력하세요.' : '기각 사유를 입력하세요.') || '';
    setProcessingId(reportId);
    setMessage('');
    try {
      await processAdminReport(reportId, status, comment);
      setMessage(status === 'ACCEPTED' ? '신고를 채택 처리했습니다.' : '신고를 기각 처리했습니다.');
      await loadReports();
    } catch (err: any) {
      setMessage(err.response?.data?.message || '신고 처리에 실패했습니다.');
    } finally {
      setProcessingId(null);
    }
  };

  return (
    <AdminShell title="신고 관리" description="접수된 신고를 확인하고 채택 또는 기각 처리합니다.">
      <div className="mb-5 flex gap-2 overflow-x-auto pb-1">
        {filters.map((item) => (
          <button
            key={item}
            type="button"
            onClick={() => setFilter(item)}
            className={`shrink-0 rounded-full px-4 py-2 text-sm font-semibold ${
              filter === item ? 'bg-[#d84315] text-white' : 'border border-[#e0e0e0] bg-white text-[#616161]'
            }`}
          >
            {item === 'ALL' ? '전체' : item}
          </button>
        ))}
      </div>

      {message && (
        <div className="mb-5 rounded-lg border border-[#e0e0e0] bg-white px-4 py-3 text-sm font-semibold text-[#616161]">
          {message}
        </div>
      )}

      {loading ? (
        <div className="rounded-2xl border border-[#e0e0e0] bg-white p-12 text-center text-[#9e9e9e]">
          <Loader2 className="mx-auto mb-3 animate-spin text-[#d84315]" />
          신고 목록을 불러오는 중...
        </div>
      ) : reports.length > 0 ? (
        <div className="space-y-3">
          {reports.map((report) => (
            <div key={report.reportId} className="rounded-2xl border border-[#e0e0e0] bg-white p-5 shadow-sm">
              <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <span className="rounded bg-[#fff3e0] px-2.5 py-1 text-xs font-bold text-[#ef6c00]">
                    {report.status}
                  </span>
                  <span className="text-sm font-bold text-[#212121]">{report.reason}</span>
                </div>
                <span className="text-xs text-[#9e9e9e]">{formatDateTime(report.createdAt)}</span>
              </div>
              <p className="text-sm text-[#616161]">신고자: {report.reporterNickname}</p>
              <p className="mt-1 text-sm text-[#616161]">대상 게시글: #{report.targetId}</p>
              <p className="mt-3 whitespace-pre-wrap rounded-lg bg-[#fafafa] p-3 text-sm text-[#424242]">
                {report.detail || '상세 내용 없음'}
              </p>
              {report.status === 'PENDING' && (
                <div className="mt-4 flex justify-end gap-2">
                  <button
                    type="button"
                    disabled={processingId === report.reportId}
                    onClick={() => handleProcess(report.reportId, 'REJECTED')}
                    className="inline-flex items-center gap-2 rounded-lg border border-red-200 px-4 py-2.5 text-sm font-bold text-red-500 hover:bg-red-50 disabled:opacity-60"
                  >
                    <XCircle size={16} />
                    기각
                  </button>
                  <button
                    type="button"
                    disabled={processingId === report.reportId}
                    onClick={() => handleProcess(report.reportId, 'ACCEPTED')}
                    className="inline-flex items-center gap-2 rounded-lg bg-[#2e7d32] px-4 py-2.5 text-sm font-bold text-white hover:bg-[#1b5e20] disabled:opacity-60"
                  >
                    <CheckCircle2 size={16} />
                    채택
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      ) : (
        <div className="rounded-2xl border border-[#e0e0e0] bg-white p-12 text-center text-[#9e9e9e]">
          표시할 신고가 없습니다.
        </div>
      )}
    </AdminShell>
  );
}

function AdminShell({ title, description, children }: { title: string; description: string; children: React.ReactNode }) {
  return (
    <div className="min-h-screen bg-gradient-to-br from-[#fff7ed] via-[#f7fbff] to-[#eaf7f1]">
      <main className="mx-auto max-w-screen-lg px-4 py-10">
        <Link to="/admin" className="mb-4 inline-flex items-center gap-1 text-sm font-semibold text-[#616161] hover:text-[#d84315]">
          <ArrowLeft size={16} />
          관리자 콘솔
        </Link>
        <h1 className="text-3xl font-bold text-[#212121]">{title}</h1>
        <p className="mb-6 mt-2 text-sm text-[#757575]">{description}</p>
        {children}
      </main>
    </div>
  );
}

function formatDateTime(value: string) {
  return new Date(value).toLocaleString('ko-KR', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}
