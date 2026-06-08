import { useEffect, useMemo, useState } from 'react';
import {
  AlertCircle,
  ArrowRight,
  ClipboardList,
  Gavel,
  Loader2,
  MessageSquare,
  RefreshCcw,
  ShieldCheck,
  Users,
} from 'lucide-react';
import { Link } from 'react-router';
import { AdminDisputeItem, getAdminDisputes } from '../../api/adminDisputeApi';
import { AdminInquiryItem, getAdminInquiries } from '../../api/adminInquiryApi';
import { AdminReportItem, getAdminReports } from '../../api/adminReportApi';
import { getAdminUsers } from '../../api/adminUserApi';

type DashboardSummary = {
  pendingReports: number | null;
  pendingInquiries: number | null;
  pendingDisputes: number | null;
  totalUsers: number | null;
  recentReports: AdminReportItem[];
  recentInquiries: AdminInquiryItem[];
  recentDisputes: AdminDisputeItem[];
};

type ActivityItem = {
  id: string;
  title: string;
  description: string;
  createdAt: string;
  path: string;
  category: string;
  color: string;
};

const emptySummary: DashboardSummary = {
  pendingReports: null,
  pendingInquiries: null,
  pendingDisputes: null,
  totalUsers: null,
  recentReports: [],
  recentInquiries: [],
  recentDisputes: [],
};

export default function AdminHomePage() {
  const [summary, setSummary] = useState<DashboardSummary>(emptySummary);
  const [loading, setLoading] = useState(true);
  const [hasError, setHasError] = useState(false);

  const loadDashboard = async () => {
    setLoading(true);
    setHasError(false);

    const [reportsResult, inquiriesResult, disputesResult, usersResult] = await Promise.allSettled([
      getAdminReports('PENDING', 0, 5),
      getAdminInquiries('PENDING', undefined, 0, 5),
      getAdminDisputes('SUBMITTED', 0, 5),
      getAdminUsers(undefined, undefined, 0, 1),
    ]);

    setHasError([reportsResult, inquiriesResult, disputesResult, usersResult].some(
      (result) => result.status === 'rejected',
    ));

    setSummary({
      pendingReports: reportsResult.status === 'fulfilled'
        ? reportsResult.value.data.data.totalElements
        : null,
      pendingInquiries: inquiriesResult.status === 'fulfilled'
        ? inquiriesResult.value.data.data.totalElements
        : null,
      pendingDisputes: disputesResult.status === 'fulfilled'
        ? disputesResult.value.data.data.totalElements
        : null,
      totalUsers: usersResult.status === 'fulfilled'
        ? usersResult.value.data.data.totalElements
        : null,
      recentReports: reportsResult.status === 'fulfilled'
        ? reportsResult.value.data.data.content
        : [],
      recentInquiries: inquiriesResult.status === 'fulfilled'
        ? inquiriesResult.value.data.data.content
        : [],
      recentDisputes: disputesResult.status === 'fulfilled'
        ? disputesResult.value.data.data.content
        : [],
    });

    setLoading(false);
  };

  useEffect(() => {
    loadDashboard();
  }, []);

  const totalPending = [summary.pendingReports, summary.pendingInquiries, summary.pendingDisputes]
    .filter((value): value is number => value !== null)
    .reduce((sum, value) => sum + value, 0);

  const recentActivities = useMemo<ActivityItem[]>(() => [
    ...summary.recentReports.map((item) => ({
      id: `report-${item.reportId}`,
      title: `신고 #${item.reportId}`,
      description: `${item.reporterNickname} · ${item.reason}`,
      createdAt: item.createdAt,
      path: `/admin/reports?reportId=${item.reportId}`,
      category: '신고',
      color: 'bg-[#fff3e0] text-[#d84315]',
    })),
    ...summary.recentInquiries.map((item) => ({
      id: `inquiry-${item.inquiryId}`,
      title: item.title,
      description: `${item.userNickname} · ${item.type}`,
      createdAt: item.createdAt,
      path: `/admin/inquiries?inquiryId=${item.inquiryId}`,
      category: '문의',
      color: 'bg-[#e3f2fd] text-[#1565c0]',
    })),
    ...summary.recentDisputes.map((item) => ({
      id: `dispute-${item.disputeId}`,
      title: `이의제기 #${item.disputeId}`,
      description: `${item.applicantNickname}님의 요청`,
      createdAt: item.submittedAt,
      path: `/admin/disputes?disputeId=${item.disputeId}`,
      category: '이의제기',
      color: 'bg-[#f3e5f5] text-[#7b1fa2]',
    })),
  ].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()), [summary]);

  const cards = [
    {
      label: '신고 처리 대기',
      value: summary.pendingReports,
      icon: ClipboardList,
      path: '/admin/reports',
      color: 'text-[#d84315]',
      background: 'bg-[#fff3e0]',
      highlightWhenPending: true,
    },
    {
      label: '문의 답변 대기',
      value: summary.pendingInquiries,
      icon: MessageSquare,
      path: '/admin/inquiries',
      color: 'text-[#1565c0]',
      background: 'bg-[#e3f2fd]',
      highlightWhenPending: true,
    },
    {
      label: '이의제기 검토 대기',
      value: summary.pendingDisputes,
      icon: Gavel,
      path: '/admin/disputes',
      color: 'text-[#7b1fa2]',
      background: 'bg-[#f3e5f5]',
      highlightWhenPending: true,
    },
    {
      label: '전체가입유저',
      value: summary.totalUsers,
      icon: Users,
      path: '/admin/users',
      color: 'text-[#2e7d32]',
      background: 'bg-[#e8f5e9]',
      highlightWhenPending: false,
    },
  ];

  return (
    <div className="mx-auto max-w-screen-xl">
      <div className="mb-7 flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <div className="flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-[#fff3e0]">
              <ShieldCheck className="text-[#d84315]" size={24} />
            </div>
            <div>
              <p className="text-xs font-bold tracking-wider text-[#d84315]">ADMIN DASHBOARD</p>
              <h1 className="text-3xl font-bold text-[#212121]">운영 현황</h1>
            </div>
          </div>
        </div>

        <button
          type="button"
          onClick={loadDashboard}
          disabled={loading}
          className="inline-flex h-10 items-center justify-center gap-2 rounded-lg border border-[#e0e0e0] bg-white px-4 text-sm font-bold text-[#616161] hover:border-[#d84315] hover:text-[#d84315] disabled:opacity-60"
        >
          <RefreshCcw className={loading ? 'animate-spin' : ''} size={16} />
          새로고침
        </button>
      </div>

      {hasError && (
        <div className="mb-5 flex items-center gap-2 rounded-xl border border-[#ffe0b2] bg-[#fff8e1] px-4 py-3 text-sm text-[#8d6e63]">
          <AlertCircle size={17} />
          일부 운영 데이터를 불러오지 못했습니다. 확인 가능한 데이터만 표시합니다.
        </div>
      )}

      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {cards.map(({ label, value, icon: Icon, path, color, background, highlightWhenPending }) => (
          <Link
            key={label}
            to={path}
            className="group rounded-2xl border border-[#e0e0e0] bg-white p-5 shadow-sm transition hover:-translate-y-0.5 hover:border-[#d84315] hover:shadow-md"
          >
            <div className="flex items-start justify-between">
              <div className={`flex h-10 w-10 items-center justify-center rounded-xl ${background}`}>
                <Icon className={color} size={21} />
              </div>
              <ArrowRight className="text-[#bdbdbd] transition group-hover:translate-x-1 group-hover:text-[#d84315]" size={17} />
            </div>
            <p className="mt-5 text-sm font-semibold text-[#757575]">{label}</p>
            <p className={`mt-1 text-3xl font-bold ${
              highlightWhenPending && value !== null && value > 0
                ? 'text-[#d84315]'
                : 'text-[#212121]'
            }`}>
              {loading ? <Loader2 className="animate-spin text-[#bdbdbd]" size={24} /> : value ?? '-'}
            </p>
          </Link>
        ))}
      </section>

      <section className="mt-7 min-h-[430px] overflow-hidden rounded-2xl border border-[#e0e0e0] bg-white shadow-sm">
        <div className="flex items-center justify-between border-b border-[#eeeeee] px-6 py-5">
          <div>
            <h2 className="text-xl font-bold text-[#212121]">처리 대기 목록</h2>
            <p className="mt-1 text-sm text-[#757575]">
              처리 대기 중인 신고·문의·이의제기를 최신순으로 보여줍니다. 총 {loading ? '-' : totalPending}건
            </p>
          </div>
        </div>

        {loading ? (
          <div className="flex min-h-[330px] items-center justify-center gap-2 text-sm text-[#9e9e9e]">
            <Loader2 className="animate-spin" size={18} />
            운영 현황을 불러오는 중...
          </div>
        ) : recentActivities.length > 0 ? (
          <div className="divide-y divide-[#f1f1f1]">
            {recentActivities.map((activity) => (
              <Link
                key={activity.id}
                to={activity.path}
                className="group flex items-center gap-4 px-6 py-4 transition hover:bg-[#fffaf7]"
              >
                <span className={`w-16 shrink-0 rounded-full px-2.5 py-1 text-center text-xs font-bold ${activity.color}`}>
                  {activity.category}
                </span>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-bold text-[#212121]">{activity.title}</p>
                  <p className="mt-1 truncate text-xs text-[#757575]">{activity.description}</p>
                </div>
                <time className="shrink-0 text-xs font-semibold text-[#9e9e9e]">
                  {formatDateTime(activity.createdAt)}
                </time>
                <ArrowRight
                  className="shrink-0 text-[#bdbdbd] transition group-hover:translate-x-0.5 group-hover:text-[#d84315]"
                  size={16}
                />
              </Link>
            ))}
          </div>
        ) : (
          <div className="flex min-h-[330px] items-center justify-center text-sm text-[#9e9e9e]">
            최근 접수된 관리 요청이 없습니다.
          </div>
        )}
      </section>
    </div>
  );
}

function formatDateTime(value: string) {
  const date = new Date(value);
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const targetDay = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const dayDifference = Math.round((today.getTime() - targetDay.getTime()) / 86_400_000);
  const time = date.toLocaleTimeString('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });

  if (dayDifference === 0) {
    return `오늘 ${time}`;
  }

  if (dayDifference === 1) {
    return `어제 ${time}`;
  }

  if (date.getFullYear() === now.getFullYear()) {
    return `${date.getMonth() + 1}월 ${date.getDate()}일 ${time}`;
  }

  return `${date.getFullYear()}년 ${date.getMonth() + 1}월 ${date.getDate()}일`;
}
