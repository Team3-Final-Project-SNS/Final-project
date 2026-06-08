import AdminFloatingChatbot, { RiceMascot } from '../components/AdminFloatingChatbot';

export default function AdminAiPage() {
  return (
    <div className="mx-auto max-w-screen-xl">
      <div className="mb-6">
        <div className="flex items-center gap-3">
          <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-[#fff3e0] shadow-sm">
            <RiceMascot size="small" showAdminHat />
          </div>
          <div>
            <p className="text-xs font-bold tracking-wider text-[#d84315]">AI ADVISOR</p>
            <h1 className="text-3xl font-bold text-[#212121]">AI 운영 도우미</h1>
          </div>
        </div>
        <p className="mt-3 text-sm text-[#757575]">
          신고 분석, 고위험 유저 후보, 운영 현황과 관리자 처리 기준을 질문할 수 있습니다.
        </p>
      </div>

      <AdminFloatingChatbot embedded />
    </div>
  );
}
