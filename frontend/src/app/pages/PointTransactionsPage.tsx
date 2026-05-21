import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { AlertCircle, ArrowLeft, Loader2 } from 'lucide-react';
import { getPointTransactions, PointTransactionResponse, PointTransactionType } from '../../api/pointApi';

type PointFilter = 'ALL' | PointTransactionType;

const pointTypeLabels: Record<PointTransactionType, string> = {
  JOIN_BONUS: '가입 보너스',
  DEPOSIT: '책임비 예치',
  EDIT_DEPOSIT: '책임비 변경',
  REFUND: '환불',
  PARTIAL_REFUND: '일부 환불',
  PENALTY: '패널티',
};

const pointFilters: Array<{ value: PointFilter; label: string }> = [
  { value: 'ALL', label: '전체' },
  { value: 'JOIN_BONUS', label: pointTypeLabels.JOIN_BONUS },
  { value: 'DEPOSIT', label: pointTypeLabels.DEPOSIT },
  { value: 'EDIT_DEPOSIT', label: pointTypeLabels.EDIT_DEPOSIT },
  { value: 'REFUND', label: pointTypeLabels.REFUND },
  { value: 'PARTIAL_REFUND', label: pointTypeLabels.PARTIAL_REFUND },
  { value: 'PENALTY', label: pointTypeLabels.PENALTY },
];

export default function PointTransactionsPage() {
  const [transactions, setTransactions] = useState<PointTransactionResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [pointFilter, setPointFilter] = useState<PointFilter>('ALL');

  useEffect(() => {
    const fetchTransactions = async () => {
      setLoading(true);
      setError('');
      try {
        const type = pointFilter === 'ALL' ? undefined : pointFilter;
        const res = await getPointTransactions(type, 0, 20);
        setTransactions(res.data.data.content);
      } catch (err) {
        console.error('Failed to load point transactions', err);
        setError('포인트 거래 내역을 불러오는데 실패했습니다.');
      } finally {
        setLoading(false);
      }
    };

    fetchTransactions();
  }, [pointFilter]);

  return (
      <div className="mx-auto max-w-3xl">
        <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <Link
                to="/me"
                className="mb-3 inline-flex items-center gap-1 text-sm font-semibold text-[#616161] transition-colors hover:text-[#d84315]"
            >
              <ArrowLeft size={16} />
              내 정보
            </Link>
            <h1 className="text-3xl font-bold text-[#212121]">포인트 거래 내역</h1>
            <p className="mt-2 text-sm text-[#757575]">최근 포인트 변동을 확인할 수 있습니다.</p>
          </div>
          <select
              value={pointFilter}
              onChange={(event) => setPointFilter(event.target.value as PointFilter)}
              className="h-11 rounded-lg border border-[#e0e0e0] bg-white px-3 text-sm font-semibold text-[#424242] outline-none transition-colors focus:border-[#d84315]"
          >
            {pointFilters.map((filter) => (
                <option key={filter.value} value={filter.value}>
                  {filter.label}
                </option>
            ))}
          </select>
        </div>

        <div className="overflow-hidden rounded-2xl border border-[#e0e0e0] bg-white shadow-sm">
          {error && (
              <div className="m-5 flex items-start gap-2 rounded-lg border border-[#ef5350] bg-[#ffebee] px-4 py-3 text-sm text-[#c62828]">
                <AlertCircle size={18} className="mt-0.5 shrink-0" />
                <span>{error}</span>
              </div>
          )}

          {loading ? (
              <div className="flex items-center justify-center gap-2 p-12 text-sm text-[#757575]">
                <Loader2 className="animate-spin text-[#d84315]" size={18} />
                거래 내역을 불러오는 중...
              </div>
          ) : transactions.length > 0 ? (
              <div className="divide-y divide-[#eeeeee]">
                {transactions.map((transaction) => (
                    <PointTransactionItem key={transaction.transactionId} transaction={transaction} />
                ))}
              </div>
          ) : (
              <div className="p-12 text-center text-sm font-semibold text-[#9e9e9e]">
                표시할 포인트 거래 내역이 없습니다.
              </div>
          )}
        </div>
      </div>
  );
}

function PointTransactionItem({ transaction }: { transaction: PointTransactionResponse }) {
  const isPositive = transaction.amount > 0;
  const amountClass = isPositive ? 'text-[#2e7d32]' : 'text-[#d84315]';

  return (
      <div className="flex flex-col gap-3 p-5 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <span className="rounded-md bg-[#fff3e0] px-2 py-1 text-xs font-bold text-[#d84315]">
              {pointTypeLabels[transaction.transactionType]}
            </span>
            <span className="text-xs font-semibold text-[#9e9e9e]">
              {formatDateTime(transaction.createdAt)}
            </span>
          </div>
          <p className="mt-2 text-sm font-semibold text-[#212121]">
            {transaction.description || pointTypeLabels[transaction.transactionType]}
          </p>
        </div>
        <div className="text-left sm:text-right">
          <p className={`text-lg font-bold ${amountClass}`}>{formatPointAmount(transaction.amount)}</p>
          <p className="mt-1 text-xs font-semibold text-[#9e9e9e]">
            잔액 {transaction.balanceAfter.toLocaleString()}P
          </p>
        </div>
      </div>
  );
}

function formatPointAmount(amount: number) {
  const sign = amount > 0 ? '+' : '';
  return `${sign}${amount.toLocaleString()}P`;
}

function formatDateTime(value: string) {
  return new Date(value).toLocaleString('ko-KR', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}
