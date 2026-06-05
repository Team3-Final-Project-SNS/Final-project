import { useEffect, useState } from 'react';
import { requestPayment } from '@portone/browser-sdk/v2';
import { AlertCircle, CheckCircle2, CreditCard, Loader2, RotateCcw, WalletCards } from 'lucide-react';
import {
  cancelPayment,
  createPayment,
  CreatePaymentResponse,
  failPayment,
  getMyPayments,
  GetPaymentResponse,
  PayMethod,
  PaymentStatus,
  verifyPayment,
  VerifyPaymentResponse,
} from '../../api/paymentApi';

const chargeOptions = [3000, 5000, 10000, 20000];

const payMethods: Array<{
  value: PayMethod;
  label: string;
  description: string;
  logo: string;
  logoClassName: string;
}> = [
  {
    value: 'CARD',
    label: '신용/체크카드',
    description: '카드사 선택',
    logo: 'CARD',
    logoClassName: 'bg-[#1f2937] text-white',
  },
  {
    value: 'KAKAOPAY',
    label: '카카오페이',
    description: '간편결제',
    logo: 'pay',
    logoClassName: 'bg-[#fee500] text-[#191919]',
  },
  {
    value: 'TOSSPAY',
    label: '토스페이',
    description: '간편결제',
    logo: 'toss',
    logoClassName: 'bg-[#0064ff] text-white',
  },
  {
    value: 'NAVERPAY',
    label: '네이버페이',
    description: '간편결제',
    logo: 'N pay',
    logoClassName: 'bg-[#03c75a] text-white',
  },
];

const statusLabels: Record<PaymentStatus, string> = {
  READY: '결제 대기',
  PAID: '결제 완료',
  CANCELLED: '결제 취소',
  FAILED: '결제 실패',
};

const statusClassNames: Record<PaymentStatus, string> = {
  READY: 'bg-[#fff3e0] text-[#ef6c00]',
  PAID: 'bg-[#e8f5e9] text-[#2e7d32]',
  CANCELLED: 'bg-[#eeeeee] text-[#616161]',
  FAILED: 'bg-[#ffebee] text-[#c62828]',
};

const portOneStoreId = import.meta.env.VITE_PORTONE_STORE_ID;
const portOneChannelKey = import.meta.env.VITE_PORTONE_CHANNEL_KEY;

export default function PaymentPage() {
  const [selectedPoint, setSelectedPoint] = useState(5000);
  const [payMethod, setPayMethod] = useState<PayMethod>('CARD');
  const [preparedPayment, setPreparedPayment] = useState<CreatePaymentResponse | null>(null);
  const [verifiedPayment, setVerifiedPayment] = useState<VerifyPaymentResponse | null>(null);
  const [payments, setPayments] = useState<GetPaymentResponse[]>([]);
  const [impUid, setImpUid] = useState('');
  const [loading, setLoading] = useState(false);
  const [historyLoading, setHistoryLoading] = useState(true);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    loadPayments();
  }, []);

  const loadPayments = async () => {
    setHistoryLoading(true);
    try {
      const response = await getMyPayments(0, 20);
      setPayments(response.data.data.content);
    } catch (err) {
      setError(getErrorMessage(err, '결제 내역을 불러오지 못했습니다.'));
    } finally {
      setHistoryLoading(false);
    }
  };

  const handleCreatePayment = async () => {
    setLoading(true);
    setMessage('');
    setError('');
    setVerifiedPayment(null);

    try {
      const response = await createPayment(selectedPoint, payMethod);
      const payment = response.data.data;
      setPreparedPayment(payment);

      if (!portOneStoreId || !portOneChannelKey) {
        setMessage('결제 준비가 완료되었습니다. PortOne 환경변수 설정 후 결제창을 호출할 수 있습니다.');
        await loadPayments();
        return;
      }

      // 백엔드에서 생성한 merchantUid를 PortOne 결제 ID로 사용해야 서버 검증과 같은 결제를 조회할 수 있습니다.
      const paymentResponse = await requestPayment({
        storeId: portOneStoreId,
        channelKey: portOneChannelKey,
        paymentId: payment.merchantUid,
        orderName: `한끼팟 ${payment.chargePoint.toLocaleString()}P 충전`,
        totalAmount: payment.amount,
        currency: 'KRW',
        payMethod: getPortOnePayMethod(payMethod),
        easyPay: getPortOneEasyPay(payMethod),
        redirectUrl: `${window.location.origin}/payments`,
      });

      if (!paymentResponse) {
        await notifyPaymentFailed(payment.paymentId);
        setMessage('결제창이 닫혔습니다. 결제를 완료했다면 내역을 새로고침해 주세요.');
        await loadPayments();
        return;
      }

      if (paymentResponse.code) {
        await notifyPaymentFailed(payment.paymentId);
        setError(paymentResponse.message || 'PortOne 결제가 완료되지 않았습니다.');
        await loadPayments();
        return;
      }

      // PortOne v2 응답의 paymentId를 백엔드 verify API의 impUid 필드로 전달합니다.
      const verifyResponse = await verifyPayment(payment.paymentId, paymentResponse.paymentId);
      setVerifiedPayment(verifyResponse.data.data);
      setMessage('결제가 완료되어 포인트가 지급되었습니다.');
      await loadPayments();
    } catch (err) {
      console.error('Payment process failed', err);
      setError(getErrorMessage(err, '결제 처리에 실패했습니다.'));
    } finally {
      setLoading(false);
    }
  };

  const handleVerifyPayment = async () => {
    if (!preparedPayment) {
      setError('먼저 결제 준비를 진행해 주세요.');
      return;
    }

    if (!impUid.trim()) {
      setError('PortOne impUid를 입력해 주세요.');
      return;
    }

    setLoading(true);
    setMessage('');
    setError('');

    try {
      const response = await verifyPayment(preparedPayment.paymentId, impUid.trim());
      setVerifiedPayment(response.data.data);
      setMessage('결제 검증이 완료되어 포인트가 지급되었습니다.');
      setImpUid('');
      await loadPayments();
    } catch (err) {
      console.error('Payment verification failed', err);
      setError(getErrorMessage(err, '결제 검증에 실패했습니다.'));
    } finally {
      setLoading(false);
    }
  };

  const handleCancelPayment = async (paymentId: number) => {
    const confirmed = window.confirm('해당 결제를 취소하시겠습니까?');

    if (!confirmed) {
      return;
    }

    setLoading(true);
    setMessage('');
    setError('');

    try {
      await cancelPayment(paymentId);
      setMessage('결제 취소가 완료되었습니다.');
      await loadPayments();
    } catch (err) {
      setError(getErrorMessage(err, '결제 취소에 실패했습니다.'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <section className="rounded-2xl border border-[#e0e0e0] bg-white p-8 shadow-sm">
        <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-[#fff3e0]">
              <CreditCard className="text-[#d84315]" size={34} />
            </div>
            <h1 className="text-3xl font-bold text-[#212121]">결제</h1>
            <p className="mt-2 text-sm font-semibold text-[#757575]">
              포인트 충전 결제를 생성하고 검증할 수 있습니다.
            </p>
          </div>
          <button
            type="button"
            onClick={loadPayments}
            className="inline-flex h-11 items-center justify-center gap-2 rounded-lg border border-[#e0e0e0] bg-white px-4 text-sm font-bold text-[#424242] transition hover:border-[#d84315] hover:text-[#d84315]"
          >
            <RotateCcw size={17} />
            내역 새로고침
          </button>
        </div>

        {message && (
          <div className="mb-5 flex items-start gap-2 rounded-lg border border-[#66bb6a] bg-[#e8f5e9] px-4 py-3 text-sm font-semibold text-[#2e7d32]">
            <CheckCircle2 size={18} className="mt-0.5 shrink-0" />
            <span>{message}</span>
          </div>
        )}

        {error && (
          <div className="mb-5 flex items-start gap-2 rounded-lg border border-[#ef5350] bg-[#ffebee] px-4 py-3 text-sm font-semibold text-[#c62828]">
            <AlertCircle size={18} className="mt-0.5 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        <div className="grid gap-5 lg:grid-cols-[1.1fr_0.9fr]">
          <div className="rounded-xl border border-[#eeeeee] p-5">
            <h2 className="text-xl font-bold text-[#212121]">포인트 충전</h2>
            <p className="mt-1 text-sm font-semibold text-[#757575]">충전할 포인트와 결제 수단을 선택하세요.</p>

            <div className="mt-5 grid grid-cols-2 gap-3 sm:grid-cols-4">
              {chargeOptions.map((point) => (
                <button
                  key={point}
                  type="button"
                  onClick={() => setSelectedPoint(point)}
                  className={`h-20 rounded-xl border text-center transition ${
                    selectedPoint === point
                      ? 'border-[#d84315] bg-[#fff3e0] text-[#d84315]'
                      : 'border-[#e0e0e0] bg-white text-[#424242] hover:border-[#d84315]'
                  }`}
                >
                  <span className="block text-lg font-extrabold">{point.toLocaleString()}P</span>
                  <span className="mt-1 block text-xs font-semibold">{point.toLocaleString()}원</span>
                </button>
              ))}
            </div>

            <div className="mt-5">
              <p className="text-sm font-bold text-[#424242]">결제 수단</p>
              <div className="mt-2 grid grid-cols-2 gap-3">
                {payMethods.map((method) => {
                  const isSelected = payMethod === method.value;

                  return (
                    <button
                      key={method.value}
                      type="button"
                      onClick={() => setPayMethod(method.value)}
                      className={`flex min-h-24 items-center gap-3 rounded-xl border bg-white p-4 text-left transition ${
                        isSelected
                          ? 'border-[#d84315] bg-[#fff7ed] shadow-sm ring-2 ring-[#ffccbc]'
                          : 'border-[#e0e0e0] hover:border-[#d84315] hover:bg-[#fffaf5]'
                      }`}
                    >
                      <span
                        className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-xl text-xs font-black tracking-tight ${method.logoClassName}`}
                        aria-hidden="true"
                      >
                        {method.logo}
                      </span>
                      <span className="min-w-0">
                        <span className={`block text-sm font-extrabold ${isSelected ? 'text-[#d84315]' : 'text-[#212121]'}`}>
                          {method.label}
                        </span>
                        <span className="mt-1 block text-xs font-semibold text-[#757575]">
                          {method.description}
                        </span>
                      </span>
                    </button>
                  );
                })}
              </div>
            </div>

            <button
              type="button"
              onClick={handleCreatePayment}
              disabled={loading}
              className="mt-5 inline-flex h-12 w-full items-center justify-center gap-2 rounded-lg bg-[#d84315] px-4 text-sm font-bold text-white shadow-sm transition hover:bg-[#bf360c] disabled:cursor-not-allowed disabled:bg-[#bdbdbd]"
            >
              {loading ? <Loader2 className="animate-spin" size={18} /> : <WalletCards size={18} />}
              결제하기
            </button>
          </div>

          <div className="rounded-xl border border-[#eeeeee] p-5">
            <h2 className="text-xl font-bold text-[#212121]">결제 검증</h2>
            <p className="mt-1 text-sm font-semibold text-[#757575]">
              결제창 성공 후 자동 검증됩니다. 필요하면 결제 ID를 직접 입력해 재검증할 수 있습니다.
            </p>

            {preparedPayment ? (
              <div className="mt-5 rounded-lg bg-[#fafafa] p-4 text-sm">
                <InfoRow label="paymentId" value={String(preparedPayment.paymentId)} />
                <InfoRow label="merchantUid" value={preparedPayment.merchantUid} />
                <InfoRow label="충전 포인트" value={`${preparedPayment.chargePoint.toLocaleString()}P`} />
                <InfoRow label="상태" value={statusLabels[preparedPayment.status]} />
              </div>
            ) : (
              <div className="mt-5 rounded-lg border border-dashed border-[#e0e0e0] p-5 text-center text-sm font-semibold text-[#9e9e9e]">
                결제 준비를 먼저 진행해 주세요.
              </div>
            )}

            <label className="mt-5 block text-sm font-bold text-[#424242]">
              결제 ID
              <input
                value={impUid}
                onChange={(event) => setImpUid(event.target.value)}
                placeholder="PortOne paymentId 또는 merchantUid"
                className="mt-2 h-12 w-full rounded-lg border border-[#e0e0e0] px-3 text-sm font-semibold outline-none transition focus:border-[#d84315]"
              />
            </label>

            <button
              type="button"
              onClick={handleVerifyPayment}
              disabled={loading || !preparedPayment}
              className="mt-5 inline-flex h-12 w-full items-center justify-center gap-2 rounded-lg border border-[#d84315] bg-white px-4 text-sm font-bold text-[#d84315] transition hover:bg-[#fff3e0] disabled:cursor-not-allowed disabled:border-[#bdbdbd] disabled:text-[#9e9e9e]"
            >
              {loading ? <Loader2 className="animate-spin" size={18} /> : <CheckCircle2 size={18} />}
              결제 검증
            </button>

            {verifiedPayment && (
              <div className="mt-5 rounded-lg bg-[#e8f5e9] p-4 text-sm">
                <InfoRow label="지급 포인트" value={`${verifiedPayment.chargePoint.toLocaleString()}P`} />
                <InfoRow label="거래 후 잔액" value={`${verifiedPayment.balanceAfter.toLocaleString()}P`} />
                <InfoRow label="완료 시각" value={formatDateTime(verifiedPayment.completedAt)} />
              </div>
            )}
          </div>
        </div>
      </section>

      <section className="overflow-hidden rounded-2xl border border-[#e0e0e0] bg-white shadow-sm">
        <div className="border-b border-[#eeeeee] px-6 py-5">
          <h2 className="text-xl font-bold text-[#212121]">내 결제 내역</h2>
          <p className="mt-1 text-sm font-semibold text-[#757575]">최근 결제 상태를 확인할 수 있습니다.</p>
        </div>

        {historyLoading ? (
          <div className="flex items-center justify-center gap-2 p-12 text-sm font-semibold text-[#757575]">
            <Loader2 className="animate-spin text-[#d84315]" size={18} />
            결제 내역을 불러오는 중...
          </div>
        ) : payments.length > 0 ? (
          <div className="divide-y divide-[#eeeeee]">
            {payments.map((payment) => (
              <PaymentHistoryItem key={payment.paymentId} payment={payment} onCancel={handleCancelPayment} />
            ))}
          </div>
        ) : (
          <div className="p-12 text-center text-sm font-semibold text-[#9e9e9e]">결제 내역이 없습니다.</div>
        )}
      </section>
    </div>
  );
}

function PaymentHistoryItem({
  payment,
  onCancel,
}: {
  payment: GetPaymentResponse;
  onCancel: (paymentId: number) => void;
}) {
  return (
    <div className="flex flex-col gap-4 p-5 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <div className="flex flex-wrap items-center gap-2">
          <span className={`rounded-md px-2 py-1 text-xs font-bold ${statusClassNames[payment.status]}`}>
            {statusLabels[payment.status]}
          </span>
          <span className="text-xs font-semibold text-[#9e9e9e]">{formatDateTime(payment.createdAt)}</span>
        </div>
        <p className="mt-3 text-base font-bold text-[#212121]">{payment.chargePoint.toLocaleString()}P 충전</p>
        <p className="mt-1 text-sm font-semibold text-[#757575]">
          {payment.payMethod} · {payment.amount.toLocaleString()}원
        </p>
      </div>

      <div className="flex items-center gap-3 sm:justify-end">
        <div className="text-left sm:text-right">
          <p className="text-lg font-extrabold text-[#d84315]">{payment.amount.toLocaleString()}원</p>
          <p className="mt-1 text-xs font-semibold text-[#9e9e9e]">
            {payment.completedAt ? formatDateTime(payment.completedAt) : '미완료'}
          </p>
        </div>
        {payment.status === 'PAID' && (
          <button
            type="button"
            onClick={() => onCancel(payment.paymentId)}
            className="h-10 whitespace-nowrap rounded-lg border border-[#e0e0e0] px-3 text-sm font-bold text-[#616161] transition hover:border-[#d84315] hover:text-[#d84315]"
          >
            결제취소(환불)
          </button>
        )}
      </div>
    </div>
  );
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-4 py-1">
      <span className="font-bold text-[#757575]">{label}</span>
      <span className="break-all text-right font-semibold text-[#212121]">{value}</span>
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

function getErrorMessage(err: unknown, fallbackMessage: string) {
  if (typeof err === 'object' && err !== null && 'response' in err) {
    const response = (err as { response?: { data?: { message?: string } } }).response;
    return response?.data?.message || fallbackMessage;
  }

  if (typeof err === 'object' && err !== null && 'message' in err) {
    return String((err as { message?: unknown }).message || fallbackMessage);
  }

  if (typeof err === 'string') {
    return err;
  }

  return fallbackMessage;
}

// PortOne 결제 실패/취소 시 백엔드 결제 상태를 FAILED로 정리합니다.
// 이 알림 실패가 사용자 결제 흐름 전체를 깨지 않도록 에러는 콘솔에만 남깁니다.
async function notifyPaymentFailed(paymentId: number) {
  try {
    await failPayment(paymentId);
  } catch (err) {
    console.error('Payment fail notification failed', err);
  }
}

function getPortOnePayMethod(payMethod: PayMethod) {
  if (payMethod === 'CARD') {
    return 'CARD' as const;
  }

  return 'EASY_PAY' as const;
}

function getPortOneEasyPay(payMethod: PayMethod) {
  if (payMethod === 'CARD') {
    return undefined;
  }

  return {
    easyPayProvider: payMethod,
  };
}
