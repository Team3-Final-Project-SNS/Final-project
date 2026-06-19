import { useEffect, useMemo, useState } from 'react';
import { ko } from 'date-fns/locale';
import type { DateRange } from 'react-day-picker';
import {
  CalendarDays,
  CheckCircle2,
  Clock,
  CreditCard,
  Loader2,
  RefreshCcw,
  Search,
  X,
  XCircle,
} from 'lucide-react';
import { Calendar } from '../components/ui/calendar';
import { Popover, PopoverContent, PopoverTrigger } from '../components/ui/popover';
import { AdminPaymentItem, getAdminPayments } from '../../api/adminPaymentApi';
import { PaymentStatus } from '../../api/paymentApi';

type PaymentStatusFilter = 'ALL' | PaymentStatus;
type PaymentMethodFilter = 'ALL' | string;
type PaymentPeriodType = 'ALL' | 'CUSTOM' | 'TODAY' | 'LAST_7_DAYS' | 'LAST_30_DAYS' | 'THIS_MONTH' | 'YEAR';

const statusLabels: Record<PaymentStatus, string> = {
  READY: '결제 대기',
  PAID: '결제 완료',
  CANCELLED: '결제 취소',
  FAILED: '결제 실패',
};

const statusClasses: Record<PaymentStatus, string> = {
  READY: 'bg-[#fff3e0] text-[#ef6c00]',
  PAID: 'bg-[#e8f5e9] text-[#2e7d32]',
  CANCELLED: 'bg-[#f5f5f5] text-[#616161]',
  FAILED: 'bg-[#ffebee] text-[#c62828]',
};

const methodLabels: Record<string, string> = {
  CARD: '카드',
  KAKAOPAY: '카카오페이',
  NAVERPAY: '네이버페이',
  TOSSPAY: '토스페이',
};

export default function AdminPaymentsPage() {
  const [payments, setPayments] = useState<AdminPaymentItem[]>([]);
  const [periodType, setPeriodType] = useState<PaymentPeriodType>('ALL');
  const [periodValue, setPeriodValue] = useState('ALL');
  const [dateRange, setDateRange] = useState<DateRange | undefined>();
  const [status, setStatus] = useState<PaymentStatusFilter>('ALL');
  const [method, setMethod] = useState<PaymentMethodFilter>('ALL');
  const [keyword, setKeyword] = useState('');
  const [searchPeriodType, setSearchPeriodType] = useState<PaymentPeriodType>('ALL');
  const [searchPeriodValue, setSearchPeriodValue] = useState('ALL');
  const [searchDateRange, setSearchDateRange] = useState<DateRange | undefined>();
  const [searchStatus, setSearchStatus] = useState<PaymentStatusFilter>('ALL');
  const [searchMethod, setSearchMethod] = useState<PaymentMethodFilter>('ALL');
  const [searchKeyword, setSearchKeyword] = useState('');
  const [selectedPayment, setSelectedPayment] = useState<AdminPaymentItem | null>(null);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState('');

  const loadPayments = async () => {
    setLoading(true);
    setMessage('');
    try {
      const response = await getAdminPayments(undefined, searchStatus === 'ALL' ? undefined : searchStatus, 0, 50);
      setPayments(response.data.data.content);
    } catch (err: any) {
      setMessage(err.response?.data?.message || '결제 내역을 불러오지 못했습니다.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadPayments();
  }, [searchStatus]);

  const availableYears = useMemo(
    () => Array.from(new Set(payments.map((payment) => String(new Date(payment.createdAt).getFullYear()))))
      .sort((a, b) => Number(b) - Number(a)),
    [payments],
  );

  const availableMethods = useMemo(
    () => Array.from(new Set(payments.map((payment) => payment.payMethod).filter(Boolean))) as string[],
    [payments],
  );

  const filteredPayments = useMemo(() => {
    const normalizedKeyword = searchKeyword.trim().toLowerCase();

    return payments.filter((payment) => {
      const matchesPeriod = isMatchedPeriod(payment, searchPeriodType, searchPeriodValue, searchDateRange);
      const matchesMethod = searchMethod === 'ALL' || payment.payMethod === searchMethod;
      const matchesKeyword =
        normalizedKeyword.length === 0 ||
        payment.merchantUid.toLowerCase().includes(normalizedKeyword) ||
        String(payment.userId).includes(normalizedKeyword) ||
        (payment.payMethod || '').toLowerCase().includes(normalizedKeyword) ||
        payment.chargePackage.toLowerCase().includes(normalizedKeyword);

      return matchesPeriod && matchesMethod && matchesKeyword;
    });
  }, [payments, searchDateRange, searchKeyword, searchMethod, searchPeriodType, searchPeriodValue]);

  const dashboardStats = useMemo(() => {
    const paidPayments = filteredPayments.filter((payment) => payment.status === 'PAID');
    const readyPayments = filteredPayments.filter((payment) => payment.status === 'READY');
    const issuePayments = filteredPayments.filter((payment) => payment.status === 'FAILED' || payment.status === 'CANCELLED');

    return {
      totalPaidAmount: paidPayments.reduce((sum, payment) => sum + payment.amount, 0),
      paidCount: paidPayments.length,
      readyCount: readyPayments.length,
      issueCount: issuePayments.length,
    };
  }, [filteredPayments]);

  const appliedPeriodLabel = useMemo(
    () => getPeriodLabel(searchPeriodType, searchPeriodValue, searchDateRange),
    [searchDateRange, searchPeriodType, searchPeriodValue],
  );

  const handlePeriodTypeChange = (nextPeriodType: PaymentPeriodType) => {
    setPeriodType(nextPeriodType);

    if (nextPeriodType === 'YEAR') {
      setPeriodValue(availableYears[0] || 'ALL');
      return;
    }

    if (nextPeriodType === 'CUSTOM') {
      setDateRange(undefined);
      setPeriodValue('ALL');
      return;
    }

    const quickRange = getQuickDateRange(nextPeriodType);
    if (quickRange) {
      setDateRange(quickRange);
      setPeriodValue('ALL');
      return;
    }

    setPeriodValue('ALL');
    setDateRange(undefined);
  };

  const handleReset = () => {
    setPeriodType('ALL');
    setPeriodValue('ALL');
    setDateRange(undefined);
    setStatus('ALL');
    setMethod('ALL');
    setKeyword('');
    setSearchPeriodType('ALL');
    setSearchPeriodValue('ALL');
    setSearchDateRange(undefined);
    setSearchStatus('ALL');
    setSearchMethod('ALL');
    setSearchKeyword('');
  };

  const handleSearch = () => {
    setSearchPeriodType(periodType);
    setSearchPeriodValue(periodValue);
    setSearchDateRange(periodType === 'CUSTOM' ? dateRange : getQuickDateRange(periodType));
    setSearchStatus(status);
    setSearchMethod(method);
    setSearchKeyword(keyword);
  };

  return (
    <div>
      <main className="mx-auto max-w-screen-xl">
        <div className="mb-7 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
          <div className="flex items-center gap-4">
            <div className="flex h-14 w-14 items-center justify-center rounded-full bg-[#fff3e0]">
              <CreditCard className="text-[#d84315]" size={30} />
            </div>
            <div>
              <h1 className="text-3xl font-bold text-[#212121]">결제 내역 관리</h1>
              <p className="mt-1 text-sm text-[#757575]">포인트 충전 결제 상태와 처리 내역을 확인합니다.</p>
            </div>
          </div>

          <button
            type="button"
            onClick={handleReset}
            className="inline-flex h-11 items-center justify-center gap-2 rounded-lg border border-[#e0e0e0] bg-white px-4 text-sm font-bold text-[#616161] transition-colors hover:border-[#d84315] hover:text-[#d84315]"
          >
            <RefreshCcw size={16} />
            조건 초기화
          </button>
        </div>

        {message && (
          <div className="mb-5 rounded-lg border border-[#ef5350] bg-[#ffebee] px-4 py-3 text-sm font-semibold text-[#c62828]">
            {message}
          </div>
        )}

        <div className="mb-5 grid gap-3 md:grid-cols-4">
          <DashboardStatCard title="총 결제액" value={`${dashboardStats.totalPaidAmount.toLocaleString()}원`} description={`${appliedPeriodLabel} · 결제 완료 기준`} />
          <DashboardStatCard title="결제 완료" value={`${dashboardStats.paidCount}건`} description={`${appliedPeriodLabel} · 포인트 지급 완료`} />
          <DashboardStatCard title="결제 대기" value={`${dashboardStats.readyCount}건`} description={`${appliedPeriodLabel} · 검증 또는 결제 진행 전`} />
          <DashboardStatCard title="취소/실패" value={`${dashboardStats.issueCount}건`} description={`${appliedPeriodLabel} · 확인이 필요한 결제`} />
        </div>

        <section className="mb-5 rounded-2xl border border-[#e0e0e0] bg-white p-4 shadow-sm">
          <div>
            <div className="mb-2 flex items-center gap-2 text-sm font-bold text-[#d84315]">
              <CalendarDays size={17} />
              기간 조회
            </div>

            <div className="flex flex-wrap items-center gap-2">
              <PeriodTypeButton label="전체" isActive={periodType === 'ALL'} onClick={() => handlePeriodTypeChange('ALL')} />
              <PeriodTypeButton label="직접 선택" isActive={periodType === 'CUSTOM'} onClick={() => handlePeriodTypeChange('CUSTOM')} />
              <PeriodTypeButton label="오늘" isActive={periodType === 'TODAY'} onClick={() => handlePeriodTypeChange('TODAY')} />
              <PeriodTypeButton label="최근 7일" isActive={periodType === 'LAST_7_DAYS'} onClick={() => handlePeriodTypeChange('LAST_7_DAYS')} />
              <PeriodTypeButton label="최근 30일" isActive={periodType === 'LAST_30_DAYS'} onClick={() => handlePeriodTypeChange('LAST_30_DAYS')} />
              <PeriodTypeButton label="이번 달" isActive={periodType === 'THIS_MONTH'} onClick={() => handlePeriodTypeChange('THIS_MONTH')} />
              <PeriodTypeButton label="연도별" isActive={periodType === 'YEAR'} onClick={() => handlePeriodTypeChange('YEAR')} />

              {periodType === 'CUSTOM' && (
                <Popover>
                  <PopoverTrigger asChild>
                    <button type="button" className="inline-flex h-11 w-full items-center justify-center gap-2 rounded-lg border border-[#d84315] bg-white px-4 text-sm font-bold text-[#424242] sm:min-w-[250px]">
                      <CalendarDays size={16} className="text-[#d84315]" />
                      {formatDateRange(dateRange)}
                    </button>
                  </PopoverTrigger>
                  <PopoverContent align="start" className="max-w-[calc(100vw-2rem)] overflow-x-auto border-[#eeeeee] bg-white p-0">
                    <Calendar
                      mode="range"
                      selected={dateRange}
                      onSelect={setDateRange}
                      numberOfMonths={2}
                      showOutsideDays
                      fixedWeeks
                      locale={ko}
                      defaultMonth={dateRange?.from}
                      classNames={{
                        day_range_start: 'day-range-start bg-[#d84315] text-white hover:bg-[#d84315] hover:text-white',
                        day_range_end: 'day-range-end bg-[#d84315] text-white hover:bg-[#d84315] hover:text-white',
                        day_range_middle: 'aria-selected:bg-[#f1f3f5] aria-selected:text-[#212121]',
                        day_today: 'bg-white font-bold !text-[#d84315]',
                        day_outside: 'invisible pointer-events-none',
                      }}
                    />
                  </PopoverContent>
                </Popover>
              )}

              {periodType === 'YEAR' && (
                <select value={periodValue} onChange={(event) => setPeriodValue(event.target.value)} className="h-11 min-w-32 rounded-lg border border-[#d84315] bg-white px-3 text-sm font-bold text-[#424242] outline-none">
                  {availableYears.map((year) => <option key={year} value={year}>{year}년</option>)}
                </select>
              )}
            </div>
          </div>

          <div className="mt-4 border-t border-[#eeeeee] pt-4">
            <p className="mb-2 text-sm font-bold text-[#424242]">결제 조건</p>
            <div className="grid gap-3 md:grid-cols-[1fr_1fr_2fr_auto]">
              <select value={status} onChange={(event) => setStatus(event.target.value as PaymentStatusFilter)} className="h-11 rounded-lg border border-[#e0e0e0] bg-white px-3 text-sm font-bold text-[#424242] outline-none focus:border-[#d84315]">
                <option value="ALL">전체 상태</option>
                {Object.entries(statusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
              </select>

              <select value={method} onChange={(event) => setMethod(event.target.value)} className="h-11 rounded-lg border border-[#e0e0e0] bg-white px-3 text-sm font-bold text-[#424242] outline-none focus:border-[#d84315]">
                <option value="ALL">전체 결제수단</option>
                {availableMethods.map((value) => <option key={value} value={value}>{methodLabels[value] || value}</option>)}
              </select>

              <label className="flex h-11 items-center gap-2 rounded-lg border border-[#e0e0e0] bg-white px-3">
                <Search size={16} className="text-[#9e9e9e]" />
                <input value={keyword} onChange={(event) => setKeyword(event.target.value)} onKeyDown={(event) => event.key === 'Enter' && handleSearch()} placeholder="주문번호, 유저 ID, 결제수단, 패키지 검색" className="w-full text-sm outline-none" />
              </label>

              <button type="button" onClick={handleSearch} className="inline-flex h-11 items-center justify-center gap-2 rounded-lg bg-[#d84315] px-5 text-sm font-bold text-white shadow-sm transition-colors hover:bg-[#bf360c]">
                <Search size={16} />
                조회
              </button>
            </div>
          </div>
        </section>

        <section className="overflow-hidden rounded-2xl border border-[#e0e0e0] bg-white shadow-sm">
          <div className="flex items-center justify-between border-b border-[#eeeeee] px-5 py-4">
            <div>
              <h2 className="text-lg font-bold text-[#212121]">결제 목록</h2>
              <p className="mt-1 text-xs font-semibold text-[#9e9e9e]">{appliedPeriodLabel} 기준입니다.</p>
            </div>
            <span className="text-sm font-bold text-[#d84315]">{filteredPayments.length}건</span>
          </div>

          {loading ? (
            <div className="flex min-h-56 items-center justify-center gap-2 text-sm text-[#9e9e9e]">
              <Loader2 className="animate-spin" size={18} />
              결제 내역을 불러오는 중...
            </div>
          ) : (
            <div className="overflow-x-auto">
              <div className="min-w-[1080px]">
                <div className="grid grid-cols-[0.35fr_1.5fr_0.75fr_0.85fr_0.85fr_0.8fr_0.8fr_1fr] gap-3 border-b border-[#eeeeee] bg-[#fafafa] px-5 py-3 text-xs font-bold text-[#757575]">
                  <span className="text-center">번호</span>
                  <span>주문번호</span>
                  <span>유저 ID</span>
                  <span>결제금액</span>
                  <span>충전 포인트</span>
                  <span>결제수단</span>
                  <span>상태</span>
                  <span>요청 시각</span>
                </div>

                {filteredPayments.length > 0 ? filteredPayments.map((payment, index) => (
                  <div key={payment.paymentId} className="grid grid-cols-[0.35fr_1.5fr_0.75fr_0.85fr_0.85fr_0.8fr_0.8fr_1fr] gap-3 border-b border-[#f5f5f5] px-5 py-4 text-sm last:border-b-0">
                    <span className="text-center font-semibold text-[#9e9e9e]">{index + 1}</span>
                    <button type="button" onClick={() => setSelectedPayment(payment)} className="w-fit text-left text-sm font-bold text-[#212121] underline-offset-4 hover:underline">
                      {payment.merchantUid}
                    </button>
                    <span className="font-bold text-[#424242]">#{payment.userId}</span>
                    <span className="font-bold text-[#212121]">{payment.amount.toLocaleString()}원</span>
                    <span className="font-bold text-[#424242]">{payment.chargePoint.toLocaleString()}P</span>
                    <span className="text-[#616161]">{payment.payMethod ? methodLabels[payment.payMethod] || payment.payMethod : '-'}</span>
                    <span><span className={`rounded px-2.5 py-1 text-xs font-bold ${statusClasses[payment.status]}`}>{statusLabels[payment.status]}</span></span>
                    <span className="font-semibold text-[#616161]">{formatDateTime(payment.createdAt)}</span>
                  </div>
                )) : (
                  <div className="p-12 text-center text-sm font-semibold text-[#9e9e9e]">조건에 맞는 결제 내역이 없습니다.</div>
                )}
              </div>
            </div>
          )}
        </section>
      </main>

      {selectedPayment && <PaymentDetailModal payment={selectedPayment} onClose={() => setSelectedPayment(null)} />}
    </div>
  );
}

function DashboardStatCard({ title, value, description }: { title: string; value: string; description: string }) {
  return (
    <div className="rounded-2xl border border-[#e0e0e0] bg-white px-5 py-4 shadow-sm">
      <p className="text-xs font-bold text-[#9e9e9e]">{title}</p>
      <p className="mt-1 text-2xl font-bold text-[#212121]">{value}</p>
      <p className="mt-1 text-xs font-semibold text-[#757575]">{description}</p>
    </div>
  );
}

function PeriodTypeButton({ label, isActive, onClick }: { label: string; isActive: boolean; onClick: () => void }) {
  return (
    <button type="button" onClick={onClick} className={`h-11 rounded-lg border px-3 text-sm font-bold transition-colors ${isActive ? 'border-[#d84315] bg-[#d84315] text-white shadow-sm' : 'border-[#e0e0e0] bg-white text-[#424242] hover:border-[#d84315] hover:text-[#d84315]'}`}>
      {label}
    </button>
  );
}

function PaymentDetailModal({ payment, onClose }: { payment: AdminPaymentItem; onClose: () => void }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
      <div className="w-full max-w-xl rounded-2xl bg-white p-6 shadow-xl">
        <div className="mb-5 flex items-start justify-between gap-4">
          <div>
            <p className="text-sm font-bold text-[#d84315]">주문번호</p>
            <h2 className="mt-1 break-all text-2xl font-bold text-[#212121]">{payment.merchantUid}</h2>
          </div>
          <button type="button" onClick={onClose} className="rounded-full p-2 text-[#757575] transition-colors hover:bg-[#f5f5f5] hover:text-[#212121]">
            <X size={22} />
          </button>
        </div>

        <div className="rounded-xl bg-[#fafafa] p-4">
          <DetailRow label="유저 ID" value={`#${payment.userId}`} />
          <DetailRow label="충전 패키지" value={payment.chargePackage} />
          <DetailRow label="결제 금액" value={`${payment.amount.toLocaleString()}원`} />
          <DetailRow label="충전 포인트" value={`${payment.chargePoint.toLocaleString()}P`} />
          <DetailRow label="결제 수단" value={payment.payMethod ? methodLabels[payment.payMethod] || payment.payMethod : '-'} />
          <DetailRow label="상태" value={<span className={`inline-flex items-center gap-1 rounded px-2.5 py-1 text-xs font-bold ${statusClasses[payment.status]}`}>{payment.status === 'PAID' ? <CheckCircle2 size={13} /> : payment.status === 'READY' ? <Clock size={13} /> : <XCircle size={13} />}{statusLabels[payment.status]}</span>} />
          <DetailRow label="요청 시각" value={formatDateTime(payment.createdAt)} />
          <DetailRow label="완료 시각" value={payment.completedAt ? formatDateTime(payment.completedAt) : '-'} />
          <DetailRow label="취소 시각" value={payment.cancelledAt ? formatDateTime(payment.cancelledAt) : '-'} />
          <DetailRow label="취소/실패 사유" value={payment.cancelReason || payment.failReason || '없음'} />
        </div>
      </div>
    </div>
  );
}

function DetailRow({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="grid gap-1 border-b border-[#eeeeee] py-3 text-sm last:border-b-0 sm:grid-cols-[120px_1fr] sm:gap-4">
      <span className="font-bold text-[#757575]">{label}</span>
      <span className="font-bold text-[#212121]">{value}</span>
    </div>
  );
}

function isMatchedPeriod(payment: AdminPaymentItem, periodType: PaymentPeriodType, periodValue: string, dateRange?: DateRange) {
  const paymentDate = new Date(payment.createdAt);

  if (periodType === 'YEAR') return String(paymentDate.getFullYear()) === periodValue;

  if (periodType !== 'ALL' && dateRange?.from) {
    const from = startOfDay(dateRange.from);
    const to = endOfDay(dateRange.to || dateRange.from);
    return paymentDate >= from && paymentDate <= to;
  }

  return true;
}

function getPeriodLabel(periodType: PaymentPeriodType, periodValue: string, dateRange?: DateRange) {
  if (periodType === 'YEAR') return `${periodValue}년`;
  if (periodType === 'TODAY') return '오늘';
  if (periodType === 'LAST_7_DAYS') return '최근 7일';
  if (periodType === 'LAST_30_DAYS') return '최근 30일';
  if (periodType === 'THIS_MONTH') return '이번 달';
  if (periodType === 'CUSTOM') return formatDateRange(dateRange);
  return '전체 기간';
}

function getQuickDateRange(periodType: PaymentPeriodType): DateRange | undefined {
  const currentDate = startOfDay(new Date());
  if (periodType === 'TODAY') return { from: currentDate, to: currentDate };
  if (periodType === 'LAST_7_DAYS') return { from: addDays(currentDate, -6), to: currentDate };
  if (periodType === 'LAST_30_DAYS') return { from: addDays(currentDate, -29), to: currentDate };
  if (periodType === 'THIS_MONTH') return { from: new Date(currentDate.getFullYear(), currentDate.getMonth(), 1), to: currentDate };
  return undefined;
}

function formatDateRange(dateRange?: DateRange) {
  if (!dateRange?.from) return '시작일 - 종료일';
  const from = formatShortDate(dateRange.from);
  const to = dateRange.to ? formatShortDate(dateRange.to) : '종료일 선택';
  return `${from} - ${to}`;
}

function formatShortDate(date: Date) {
  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}`;
}

function addDays(date: Date, amount: number) {
  const result = new Date(date);
  result.setDate(result.getDate() + amount);
  return result;
}

function startOfDay(date: Date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

function endOfDay(date: Date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate(), 23, 59, 59, 999);
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat('ko-KR', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value));
}
