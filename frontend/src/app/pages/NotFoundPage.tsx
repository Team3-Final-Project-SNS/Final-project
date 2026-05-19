import { Link } from 'react-router';

export default function NotFoundPage() {
  return (
    <div className="min-h-screen bg-[#fafafa] flex items-center justify-center p-4">
      <div className="text-center">
        <h1 className="text-6xl font-bold text-[#d84315] mb-4">404</h1>
        <p className="text-xl text-[#616161] mb-8">페이지를 찾을 수 없습니다</p>
        <Link
          to="/"
          className="inline-block px-6 py-3 bg-[#d84315] text-white rounded-lg font-semibold hover:bg-[#bf360c] transition-colors"
        >
          홈으로 돌아가기
        </Link>
      </div>
    </div>
  );
}
