import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router';
import { CheckCircle2, ClipboardList, Eye, Loader2, X, XCircle } from 'lucide-react';
import { AdminReportItem, getAdminReport, getAdminReports, processAdminReport } from '../../api/adminReportApi';
import { ReportStatus } from '../../api/reportApi';

const filters: ('ALL' | ReportStatus)[] = ['ALL', 'PENDING', 'ACCEPTED', 'REJECTED', 'WITHDRAWN'];

const statusLabels: Record<ReportStatus, string> = {
  PENDING: '처리 대기',
  ACCEPTED: '채택',
  REJECTED: '기각',
  WITHDRAWN: '취소',
};

const reasonLabels: Record<string, string> = {
  SPAM: '스팸',
  OBSCENE: '음란/부적절',
  FRAUD: '사기',
  ABUSE: '욕설/괴롭힘',
  OTHER: '기타',
};

export default function AdminReportsPage() {
  const [searchParams] = useSearchParams();
  const requestedReportId = Number(searchParams.get('reportId'));
  const [reports, setReports] = useState<AdminReportItem[]>([]);
  const [filter, setFilter] = useState<'ALL' | ReportStatus>('PENDING');
  const [loading, setLoading] = useState(true);
  const [processingId, setProcessingId] = useState<number | null>(null);
  const [detailLoadingId, setDetailLoadingId] = useState<number | null>(null);
  const [selectedReport, setSelectedReport] = useState<AdminReportItem | null>(null);
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

  useEffect(() => {
    if (Number.isInteger(requestedReportId) && requestedReportId > 0) {
      handleOpenDetail(requestedReportId);
    }
  }, [requestedReportId]);

  const handleProcess = async (reportId: number, status: 'ACCEPTED' | 'REJECTED') => {
    // 신고 채택/기각은 사유 입력 없이 즉시 처리
    setProcessingId(reportId);
    setMessage('');
    try {
      await processAdminReport(reportId, status);
      setMessage(status === 'ACCEPTED' ? '신고를 채택 처리했습니다.' : '신고를 기각 처리했습니다.');
      await loadReports();
    } catch (err: any) {
      setMessage(err.response?.data?.message || '신고 처리에 실패했습니다.');
    } finally {
      setProcessingId(null);
    }
  };

  const handleOpenDetail = async (reportId: number) => {
    setDetailLoadingId(reportId);
    setMessage('');

    try {
      const response = await getAdminReport(reportId);
      setSelectedReport(response.data.data);
    } catch (err: any) {
      setMessage(err.response?.data?.message || '신고 상세 정보를 불러오지 못했습니다.');
    } finally {
      setDetailLoadingId(null);
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
            {item === 'ALL' ? '전체' : statusLabels[item]}
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
                  <span className="hankki-status-badge rounded bg-[#fff3e0] px-2.5 py-1 text-xs font-bold text-[#ef6c00]">
                    {statusLabels[report.status]}
                  </span>
                  <span className="text-sm font-bold text-[#212121]">{reasonLabels[report.reason] || report.reason}</span>
                </div>
                <span className="text-xs text-[#9e9e9e]">{formatDateTime(report.createdAt)}</span>
              </div>
              <p className="text-sm text-[#616161]">신고자: {report.reporterNickname}</p>
              <p className="mt-1 text-sm text-[#616161]">대상 게시글: #{report.targetId}</p>
              <p className="mt-3 whitespace-pre-wrap rounded-lg bg-[#fafafa] p-3 text-sm text-[#424242]">
                {report.detail || '상세 내용 없음'}
              </p>
              <div className="mt-4 flex justify-end gap-2">
                <button
                  type="button"
                  disabled={detailLoadingId === report.reportId}
                  onClick={() => handleOpenDetail(report.reportId)}
                  className="inline-flex items-center gap-2 rounded-lg border border-[#e0e0e0] px-4 py-2.5 text-sm font-bold text-[#616161] hover:border-[#d84315] hover:text-[#d84315] disabled:opacity-60"
                >
                  {detailLoadingId === report.reportId ? <Loader2 className="animate-spin" size={16} /> : <Eye size={16} />}
                  상세
                </button>
                {report.status === 'PENDING' && (
                  <>
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
                  </>
                )}
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="rounded-2xl border border-[#e0e0e0] bg-white p-12 text-center text-[#9e9e9e]">
          표시할 신고가 없습니다.
        </div>
      )}
      {selectedReport && (
        <ReportDetailModal report={selectedReport} onClose={() => setSelectedReport(null)} />
      )}
    </AdminShell>
  );
}

function ReportDetailModal({ report, onClose }: { report: AdminReportItem; onClose: () => void }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/45 p-4">
      <div className="w-full max-w-lg rounded-2xl bg-white p-6 shadow-xl">
        <div className="mb-5 flex items-start justify-between gap-4">
          <div>
            <p className="text-xs font-bold text-[#d84315]">신고 #{report.reportId}</p>
            <h2 className="mt-1 text-2xl font-bold text-[#212121]">신고 상세</h2>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-full p-2 text-[#757575] hover:bg-[#f5f5f5] hover:text-[#212121]"
          >
            <X size={20} />
          </button>
        </div>

        <div className="space-y-3 rounded-xl bg-[#fafafa] p-4 text-sm">
          <InfoRow label="신고자" value={report.reporterNickname} />
          <InfoRow label="대상 게시글" value={`#${report.targetId}`} />
          <InfoRow label="신고 사유" value={reasonLabels[report.reason] || report.reason} />
          <InfoRow label="처리 상태" value={statusLabels[report.status]} />
          <InfoRow label="접수 시각" value={formatDateTime(report.createdAt)} />
        </div>

        <div className="mt-4 rounded-xl border border-[#eeeeee] p-4">
          <p className="mb-2 text-sm font-bold text-[#616161]">상세 내용</p>
          <p className="whitespace-pre-wrap text-sm leading-6 text-[#424242]">
            {report.detail || '상세 내용 없음'}
          </p>
        </div>
      </div>
    </div>
  );
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-4">
      <span className="font-bold text-[#757575]">{label}</span>
      <span className="text-right font-semibold text-[#212121]">{value}</span>
    </div>
  );
}

function AdminShell({ title, description, children }: { title: string; description: string; children: React.ReactNode }) {
  return (
    <div>
      <main className="mx-auto w-full max-w-screen-xl">
        <div className="mb-6 flex items-center gap-3">
          <ClipboardList className="text-[#d84315]" size={28} />
          <div>
            <h1 className="text-3xl font-bold text-[#212121]">{title}</h1>
            <p className="mt-1 text-sm text-[#757575]">{description}</p>
          </div>
        </div>
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
