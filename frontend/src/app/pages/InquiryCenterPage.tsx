import { FormEvent, useEffect, useState } from 'react';
import { AlertCircle, ArrowLeft, FileText, HelpCircle, Loader2, MessageSquare, Send, Siren, Trash2 } from 'lucide-react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router';
import { toast } from 'sonner';
import { useAuthStatus } from '@/store/authStatusStore';
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
import {
  createDispute,
  DisputeResponse,
  DisputeType,
  getMyDispute,
  getMyDisputes,
} from '../../api/matchApi';
import AdminFloatingChatbot from '../components/AdminFloatingChatbot';

const inquiryTypes: { value: InquiryType; label: string }[] = [
  { value: 'ACCOUNT', label: '계정' },
  { value: 'PAYMENT', label: '결제' },
  { value: 'MATCH', label: '매칭' },
  { value: 'REPORT', label: '신고' },
  { value: 'USAGE', label: '이용 방법' },
  { value: 'HISTORY', label: '이용 내역' },
  { value: 'OTHER', label: '기타' },
];


const disputeTypes: { value: DisputeType; label: string }[] = [
  { value: 'FUNERAL_CEREMONY', label: '(경)조사' },
  { value: 'MEDICAL_EMERGENCY', label: '응급실' },
  { value: 'PHONE_MALFUNCTION', label: '스마트폰 고장' },
  { value: 'GPS_ERROR', label: 'GPS 인증 오류' },
  { value: 'QR_ERROR', label: 'QR 코드 인식 오류' },
];

const statusLabels: Partial<Record<InquiryAnswerStatus, string>> = {
  PENDING: '접수됨',
  READ: '확인 중',
  ANSWERED: '답변 완료',
  WITHDRAWN: '취소됨',
};

const statusClasses: Partial<Record<InquiryAnswerStatus, string>> = {
  PENDING: 'bg-[#fff3e0] text-[#ef6c00]',
  READ: 'bg-[#e3f2fd] text-[#1565c0]',
  ANSWERED: 'bg-[#e8f5e9] text-[#2e7d32]',
  WITHDRAWN: 'bg-[#f5f5f5] text-[#757575]',
};

const statusDisplayLabels: Partial<Record<InquiryAnswerStatus, string>> = {
  PENDING: '접수완료',
  READ: '열람',
  IN_PROGRESS: '열람',
  ANSWERED: '답변완료',
};

const statusDisplayClasses: Partial<Record<InquiryAnswerStatus, string | undefined>> = {
  PENDING: statusClasses.PENDING,
  READ: statusClasses.READ,
  IN_PROGRESS: statusClasses.READ,
  ANSWERED: statusClasses.ANSWERED,
};

const getInquiryStatusLabel = (status: InquiryAnswerStatus) =>
  statusDisplayLabels[status] || statusLabels[status] || status;

const getInquiryStatusClass = (status: InquiryAnswerStatus) =>
  statusDisplayClasses[status] || statusClasses[status] || 'bg-[#f5f5f5] text-[#757575]';

export default function InquiryCenterPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const requestedNoShowMatchId = searchParams.get('matchId');
  const requestedNoShowMatchIdNumber = Number(requestedNoShowMatchId);
  const hasRequestedNoShowMatchId = Number.isInteger(requestedNoShowMatchIdNumber) && requestedNoShowMatchIdNumber > 0;
  const requestedInquiryId = searchParams.get('inquiryId');
  const routeView = location.pathname.endsWith('/disputes/no-show') || searchParams.get('view') === 'noShow'
      ? 'noShow'
      : location.pathname.endsWith('/inquiries') || requestedInquiryId
          ? 'inquiry'
          : 'menu';
  const [view, setView] = useState<'menu' | 'inquiry' | 'noShow'>(
      routeView,
  );
  const [items, setItems] = useState<InquiryListItem[]>([]);
  const [selected, setSelected] = useState<InquiryDetail | null>(null);
  const [disputeItems, setDisputeItems] = useState<DisputeResponse[]>([]);
  const [selectedDispute, setSelectedDispute] = useState<DisputeResponse | null>(null);
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
  const [disputeType, setDisputeType] = useState<DisputeType>('GPS_ERROR');
  const [disputeReason, setDisputeReason] = useState('');
  const isNoShowView = view === 'noShow';
  const { isSuspended } = useAuthStatus();
  const visibleInquiryTypes = isSuspended
    ? inquiryTypes.filter((item) => item.value === 'ACCOUNT')
    : inquiryTypes;

  const openInquiryForm = () => {
    setError('');
    setSuccess('');
    setType(isSuspended ? 'ACCOUNT' : 'OTHER');
    setTitle('');
    setContent('');
    setSelected(null);
    setSelectedDispute(null);
    setView('inquiry');
    navigate('/me/support/inquiries');
  };

  const openNoShowObjectionForm = () => {
    if (isSuspended) {
      toast.warning('정지된 계정입니다. 문의하기로 이의를 제기해 주세요.');
      setView('inquiry');
      setType('ACCOUNT');
      navigate('/me/support/inquiries');
      return;
    }

    setError('');
    setSuccess('');
    setType('MATCH');
    setTitle('');
    setContent('');
    setDisputeType('GPS_ERROR');
    setDisputeReason('');
    setSelected(null);
    setSelectedDispute(null);
    setView('noShow');
    navigate('/me/support/disputes/no-show');
  };

  const loadDisputes = async () => {
    setLoading(true);
    setError('');
    try {
      const res = await getMyDisputes();
      const disputes = res.data.data
        .sort((a, b) => new Date(b.submittedAt).getTime() - new Date(a.submittedAt).getTime());

      setDisputeItems(disputes);
      setTotalPages(1);
      setPage(0);
    } catch (err) {
      console.error('Failed to load disputes', err);
      setError('노쇼 이의제기 접수 내역을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  };

  const loadInquiries = async (nextPage = page) => {
    if (isNoShowView) {
      await loadDisputes();
      return;
    }

    setLoading(true);
    setError('');
    try {
      const res = await getMyInquiries(nextPage, 10);
      setItems(res.data.data.content);
      setTotalPages(res.data.data.totalPages || 1);
    } catch (err) {
      console.error('Failed to load inquiries', err);
      setError(isNoShowView ? '노쇼 이의제기 접수 내역을 불러오지 못했습니다.' : '문의 내역을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    setView(routeView);
  }, [routeView]);

  useEffect(() => {
    if (isSuspended && view === 'noShow') {
      setView('inquiry');
      setType('ACCOUNT');
      navigate('/me/support/inquiries', { replace: true });
      return;
    }

    loadInquiries(page);
  }, [page, view, isSuspended]);

  useEffect(() => {
    if (isSuspended && type !== 'ACCOUNT') {
      setType('ACCOUNT');
    }
  }, [isSuspended, type]);


  const handleSelect = async (inquiryId: number) => {
    if (isNoShowView) {
      setDetailLoading(true);
      setError('');
      try {
        const res = await getMyDispute(inquiryId);
        setSelectedDispute(res.data.data);
        setSelected(null);
      } catch (err) {
        console.error('Failed to load dispute detail', err);
        setError('노쇼 이의제기 상세 내용을 불러오지 못했습니다.');
      } finally {
        setDetailLoading(false);
      }
      return;
    }

    setDetailLoading(true);
    setError('');
    try {
      const res = await getInquiry(inquiryId);
      setSelected(res.data.data);
      setSelectedDispute(null);
    } catch (err) {
      console.error('Failed to load inquiry detail', err);
      setError(isNoShowView ? '노쇼 이의제기 상세 내용을 불러오지 못했습니다.' : '문의 상세 내용을 불러오지 못했습니다.');
    } finally {
      setDetailLoading(false);
    }
  };

  useEffect(() => {
    const inquiryId = Number(requestedInquiryId);
    if (!Number.isInteger(inquiryId) || inquiryId <= 0) {
      return;
    }

    setView('inquiry');
    handleSelect(inquiryId);
  }, [requestedInquiryId]);

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
      const submitType = isSuspended ? 'ACCOUNT' : isNoShowView ? 'MATCH' : type;
      const res = await createInquiry({ title: title.trim(), content: content.trim(), type: submitType });
      setTitle('');
      setContent('');
      setType(isSuspended ? 'ACCOUNT' : isNoShowView ? 'MATCH' : 'OTHER');
      setSuccess(isNoShowView ? '노쇼 이의제기가 접수되었습니다.' : '문의가 접수되었습니다.');
      await loadInquiries(0);
      setPage(0);
      await handleSelect(res.data.data.inquiryId);
    } catch (err: any) {
      setError(err.response?.data?.message || '문의 접수에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleNoShowDisputeSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!hasRequestedNoShowMatchId) {
      setError('matchId가 없어 노쇼 이의제기를 접수할 수 없습니다.');
      return;
    }
    if (!disputeReason.trim()) {
      setError('이의제기 사유를 입력해주세요.');
      return;
    }

    setSubmitting(true);
    setError('');
    setSuccess('');
    try {
      const response = await createDispute(requestedNoShowMatchIdNumber, {
        disputeType,
        reason: disputeReason.trim(),
      });
      const submittedReason = disputeReason.trim();
      setDisputeReason('');
      await loadDisputes();
      setSelectedDispute({
        disputeId: response.data.data.disputeId,
        matchId: response.data.data.matchId,
        disputeType: response.data.data.disputeType,
        reason: submittedReason,
        status: response.data.data.status as DisputeResponse['status'],
        adminComment: null,
        submittedAt: response.data.data.submittedAt,
        processedAt: null,
        holdDeadlineAt: null,
      });
      setSuccess('노쇼 이의제기가 접수되었습니다.');
    } catch (err: any) {
      setError(err.response?.data?.message || '노쇼 이의제기 접수에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleCancel = async (inquiryId: number) => {
    if (!confirm(isNoShowView ? '접수한 노쇼 이의제기를 취소하시겠습니까?' : '접수한 문의를 취소하시겠습니까?')) {
      return;
    }

    setError('');
    setSuccess('');
    try {
      await cancelInquiry(inquiryId);
      setSuccess(isNoShowView ? '노쇼 이의제기 접수가 취소되었습니다.' : '문의가 취소되었습니다.');
      setSelected(null);
      await loadInquiries(page);
    } catch (err: any) {
      setError(err.response?.data?.message || (isNoShowView ? '노쇼 이의제기 접수 취소에 실패했습니다.' : '문의 취소에 실패했습니다.'));
    }
  };

  return (
    <>
      <div className="mx-auto max-w-5xl">
        <div className="mb-6">
          <h1 className="text-3xl font-bold text-[#212121]">고객센터</h1>
          <p className="mt-2 text-sm text-[#757575]">필요한 고객센터 업무를 선택해 진행할 수 있습니다.</p>
        </div>

        {error && <Notice tone="error" message={error} />}
        {success && <Notice tone="success" message={success} />}

        {view === 'menu' ? (
          <div className="grid gap-5 md:grid-cols-2">
            <button
              type="button"
              onClick={openNoShowObjectionForm}
              aria-disabled={isSuspended}
              className={`group rounded-2xl border border-[#e0e0e0] bg-white p-7 text-left shadow-sm transition-all hover:border-[#d84315] hover:shadow-md ${
                isSuspended ? 'cursor-not-allowed opacity-45' : ''
              }`}
            >
              <div className="mb-5 flex h-12 w-12 items-center justify-center rounded-full bg-[#ffebee] text-[#d84315]">
                <Siren size={24} />
              </div>
              <h2 className="text-xl font-bold text-[#212121]">노쇼 이의제기</h2>
              <p className="mt-3 text-sm leading-6 text-[#757575]">
                노쇼 처리에 대한 이의 사유를 작성하고 고객센터에 접수합니다.
              </p>
              <span className="mt-6 inline-flex items-center gap-1 text-sm font-bold text-[#d84315]">
                이의제기 작성하기
                <ArrowLeft size={15} className="rotate-180 transition-transform group-hover:translate-x-1" />
              </span>
            </button>

            <button
              type="button"
              onClick={openInquiryForm}
              className="group rounded-2xl border border-[#e0e0e0] bg-white p-7 text-left shadow-sm transition-all hover:border-[#d84315] hover:shadow-md"
            >
              <div className="mb-5 flex h-12 w-12 items-center justify-center rounded-full bg-[#fff3e0] text-[#d84315]">
                <FileText size={24} />
              </div>
              <h2 className="text-xl font-bold text-[#212121]">문의 접수</h2>
              <p className="mt-3 text-sm leading-6 text-[#757575]">
                계정, 결제, 매칭, 신고 등 일반 문의를 접수하고 답변 상태를 확인합니다.
              </p>
              <span className="mt-6 inline-flex items-center gap-1 text-sm font-bold text-[#d84315]">
                문의 작성하기
                <ArrowLeft size={15} className="rotate-180 transition-transform group-hover:translate-x-1" />
              </span>
            </button>

            <Link
              to="/me/support/faq"
              className="group rounded-2xl border border-[#e0e0e0] bg-white p-7 text-left shadow-sm transition-all hover:border-[#d84315] hover:shadow-md"
            >
              <div className="mb-5 flex h-12 w-12 items-center justify-center rounded-full bg-[#e8f5e9] text-[#2e7d32]">
                <HelpCircle size={24} />
              </div>
              <h2 className="text-xl font-bold text-[#212121]">FAQ</h2>
              <p className="mt-3 text-sm leading-6 text-[#757575]">
                자주 묻는 질문과 서비스 이용 안내를 확인합니다.
              </p>
              <span className="mt-6 inline-flex items-center gap-1 text-sm font-bold text-[#d84315]">
                FAQ 보기
                <ArrowLeft size={15} className="rotate-180 transition-transform group-hover:translate-x-1" />
              </span>
            </Link>
          </div>
        ) : (
          <>
            <button
              type="button"
              onClick={() => navigate('/me/support')}
              className="mb-5 inline-flex items-center gap-1 text-sm font-semibold text-[#616161] transition-colors hover:text-[#d84315]"
            >
              <ArrowLeft size={16} />
              고객센터
            </button>

        <div className="grid gap-6 lg:grid-cols-[360px_1fr]">
          <section className="rounded-2xl border border-[#e0e0e0] bg-white p-5 shadow-sm">
            <h2 className="mb-4 text-lg font-bold text-[#212121]">
              {isNoShowView ? '노쇼 이의제기' : '문의 접수'}
            </h2>
            {isNoShowView ? (
              <form onSubmit={handleNoShowDisputeSubmit} className="space-y-4">
                <div>
                  <label className="mb-1 block text-xs font-bold text-[#757575]">이의제기 매칭</label>
                  <div className={`rounded-lg border px-3 py-3 text-sm font-semibold ${
                    hasRequestedNoShowMatchId
                      ? 'border-[#e0e0e0] bg-[#fafafa] text-[#424242]'
                      : 'border-dashed border-[#ef9a9a] bg-[#ffebee] text-[#c62828]'
                  }`}>
                    {hasRequestedNoShowMatchId ? `연결된 매칭 #${requestedNoShowMatchId}` : '연결된 매칭 정보가 없습니다.'}
                  </div>
                </div>
                <div>
                  <label className="mb-1 block text-xs font-bold text-[#757575]">이의제기 사유</label>
                  <select
                    value={disputeType}
                    onChange={(event) => setDisputeType(event.target.value as DisputeType)}
                    className="w-full rounded-lg border border-[#e0e0e0] bg-white px-3 py-2 text-sm focus:border-[#d84315] focus:outline-none focus:ring-2 focus:ring-[#fff3e0]"
                  >
                    {disputeTypes.map((item) => (
                      <option key={item.value} value={item.value}>
                        {item.label}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="mb-1 block text-xs font-bold text-[#757575]">상세 사유</label>
                  <textarea
                    value={disputeReason}
                    onChange={(event) => setDisputeReason(event.target.value)}
                    rows={8}
                    maxLength={1000}
                    className="w-full resize-none rounded-lg border border-[#e0e0e0] px-3 py-2 text-sm focus:border-[#d84315] focus:outline-none focus:ring-2 focus:ring-[#fff3e0]"
                    placeholder="노쇼 처리에 이의가 있는 이유와 당시 상황을 자세히 입력해주세요"
                  />
                </div>
                <button
                  type="submit"
                  disabled={submitting || !hasRequestedNoShowMatchId || !disputeReason.trim()}
                  className="flex w-full items-center justify-center gap-2 rounded-lg bg-[#d84315] px-4 py-3 text-sm font-bold text-white shadow-md transition-colors hover:bg-[#bf360c] disabled:opacity-60"
                >
                  {submitting ? <Loader2 size={18} className="animate-spin" /> : <Send size={18} />}
                  이의제기 접수
                </button>
              </form>
            ) : (
            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="mb-1 block text-xs font-bold text-[#757575]">문의 유형</label>
                {isSuspended && (
                  <p className="mb-2 rounded-md bg-[#fff8e1] px-3 py-2 text-xs font-semibold text-[#6d4c41]">
                    정지된 계정은 계정/인증 문의만 접수할 수 있습니다.
                  </p>
                )}
                <select
                  value={type}
                  onChange={(event) => setType(event.target.value as InquiryType)}
                  className="w-full rounded-lg border border-[#e0e0e0] bg-white px-3 py-2 text-sm focus:border-[#d84315] focus:outline-none focus:ring-2 focus:ring-[#fff3e0]"
                >
                  {visibleInquiryTypes.map((item) => (
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
            )}
          </section>

        <section className="space-y-4">
          <div className="rounded-2xl border border-[#e0e0e0] bg-white p-5 shadow-sm">
            <h2 className="mb-4 text-lg font-bold text-[#212121]">{isNoShowView ? '노쇼 이의제기 접수 내역' : '내 문의 내역'}</h2>
            {loading ? (
              <div className="py-10 text-center text-sm text-[#9e9e9e]">{isNoShowView ? '노쇼 이의제기 접수 내역을 불러오는 중...' : '문의 내역을 불러오는 중...'}</div>
            ) : isNoShowView ? (
              disputeItems.length > 0 ? (
                <div className="space-y-2">
                  {disputeItems.map((item) => (
                    <button
                      key={item.disputeId}
                      type="button"
                      onClick={() => handleSelect(item.matchId)}
                      className="w-full rounded-xl border border-[#eeeeee] p-4 text-left transition-colors hover:border-[#d84315] hover:bg-[#fffaf7]"
                    >
                      <div className="mb-2 flex items-center justify-between gap-3">
                        <span className="rounded bg-[#fff3e0] px-2.5 py-1 text-xs font-bold text-[#ef6c00]">
                          {disputeStatusLabel(item.status)}
                        </span>
                        <span className="text-xs text-[#9e9e9e]">{formatDateTime(item.submittedAt)}</span>
                      </div>
                      <p className="font-bold text-[#212121]">매칭 #{item.matchId} 노쇼 이의제기</p>
                      <p className="mt-1 text-xs font-semibold text-[#757575]">{disputeTypeLabel(item.disputeType)}</p>
                    </button>
                  ))}
                </div>
              ) : (
                <div className="rounded-xl border border-dashed border-[#e0e0e0] p-8 text-center text-sm text-[#9e9e9e]">
                  접수한 노쇼 이의제기가 없습니다.
                </div>
              )
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
                      <span className={`rounded px-2.5 py-1 text-xs font-bold ${getInquiryStatusClass(item.answerStatus)}`}>
                        {getInquiryStatusLabel(item.answerStatus)}
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
                {isNoShowView ? '접수한 노쇼 이의제기가 없습니다.' : '접수한 문의가 없습니다.'}
              </div>
            )}
          </div>

          {totalPages > 1 && (
            <Pagination page={page} totalPages={totalPages} onChange={setPage} />
          )}

          <div className="rounded-2xl border border-[#e0e0e0] bg-white p-5 shadow-sm">
            <h2 className="mb-4 text-lg font-bold text-[#212121]">{isNoShowView ? '노쇼 이의제기 상세' : '문의 상세'}</h2>
            {detailLoading ? (
              <div className="py-10 text-center text-sm text-[#9e9e9e]">상세 내용을 불러오는 중...</div>
            ) : isNoShowView && selectedDispute ? (
              <div className="space-y-4">
                <div>
                  <div className="mb-2 flex flex-wrap items-center gap-2">
                    <span className="rounded bg-[#fff3e0] px-2.5 py-1 text-xs font-bold text-[#ef6c00]">
                      {disputeStatusLabel(selectedDispute.status)}
                    </span>
                    <span className="text-xs font-semibold text-[#757575]">매칭 #{selectedDispute.matchId}</span>
                  </div>
                  <h3 className="text-xl font-bold text-[#212121]">{disputeTypeLabel(selectedDispute.disputeType)}</h3>
                  <p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-[#616161]">{selectedDispute.reason}</p>
                  <p className="mt-3 text-xs text-[#9e9e9e]">접수일 {formatDateTime(selectedDispute.submittedAt)}</p>
                </div>

                {selectedDispute.adminComment ? (
                  <div className="rounded-xl bg-[#f8fbf8] p-4">
                    <p className="mb-2 text-sm font-bold text-[#2e7d32]">관리자 답변</p>
                    <p className="whitespace-pre-wrap text-sm leading-6 text-[#424242]">{selectedDispute.adminComment}</p>
                    {selectedDispute.processedAt && <p className="mt-3 text-xs text-[#9e9e9e]">{formatDateTime(selectedDispute.processedAt)}</p>}
                  </div>
                ) : (
                  <div className="rounded-xl bg-[#fafafa] p-4 text-sm text-[#757575]">
                    아직 관리자 답변이 등록되지 않았습니다.
                  </div>
                )}
              </div>
            ) : selected ? (
              <div className="space-y-4">
                <div>
                  <div className="mb-2 flex flex-wrap items-center gap-2">
                    <span className={`rounded px-2.5 py-1 text-xs font-bold ${getInquiryStatusClass(selected.answerStatus)}`}>
                      {getInquiryStatusLabel(selected.answerStatus)}
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
                    {isNoShowView ? '이의제기 접수 취소' : '문의 취소'}
                  </button>
                )}
              </div>
            ) : (
              <div className="flex flex-col items-center justify-center py-12 text-center text-sm text-[#9e9e9e]">
                <MessageSquare className="mb-3 text-[#d84315]" size={32} />
                {isNoShowView ? '노쇼 이의제기를 선택하면 상세 내용과 답변을 볼 수 있습니다.' : '문의를 선택하면 상세 내용과 답변을 볼 수 있습니다.'}
              </div>
            )}
          </div>
        </section>
        </div>
          </>
        )}
      </div>
      <AdminFloatingChatbot
        title="한끼팟 고객 도우미"
        subtitle="고객 전용 도우미"
        greeting="고객님, 무엇을 도와드릴까요?"
        initialMessage="안녕하세요. 한끼팟 고객 도우미입니다. 문의 전 궁금한 내용을 편하게 입력해 주세요."
        replyMessage="확인했습니다. 고객센터 이용과 문의 접수를 도와드릴게요."
        showAdminHat={false}
        useAiReportApi={false}
        useAiSupportApi={true}
      />
    </>
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

function disputeTypeLabel(type: DisputeType) {
  return disputeTypes.find((item) => item.value === type)?.label || type;
}

function disputeStatusLabel(status: DisputeResponse['status']) {
  const labels: Record<DisputeResponse['status'], string> = {
    SUBMITTED: '접수 완료',
    UNDER_REVIEW: '검토 중',
    ACCEPTED: '수용',
    PARTIALLY_ACCEPTED: '일부 수용',
    REJECTED: '기각',
    HOLD: '보류',
  };

  return labels[status] || status;
}

function formatDateTime(value: string) {
  return new Date(value).toLocaleString('ko-KR', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}
