import { useEffect, useState } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router';
import { getAccessToken } from '@/api/axiosInstance';
import { getUserMe } from '@/api/userApi';

export default function RequireAuth() {
  const location = useLocation();

  // [추가] 인증 확인이 끝났는지 여부
  // - true가 되기 전까지는 로그인 여부를 판단할 수 없으므로 리다이렉트 보류
  const [checked, setChecked] = useState(false);

  // [추가] 최종적으로 로그인된 상태인지 여부
  const [authenticated, setAuthenticated] = useState(false);

  useEffect(() => {
    const verifyAuth = async () => {
      // 1. 메모리에 accessToken이 이미 있으면 → 바로 통과
      //    (페이지 이동 시에는 메모리가 살아있으므로 매번 API를 안 호출해도 됨)
      if (getAccessToken()) {
        setAuthenticated(true);
        setChecked(true);
        return;
      }

      // 2. 메모리에 토큰이 없는 경우 (새로고침 직후 등)
      //    → 가벼운 인증 필요 API를 호출해서 axiosInstance의
      //      401 인터셉터가 refresh_token/device_id 쿠키로
      //      자동 재발급을 시도하도록 유도
      try {
        await getUserMe();
        // 재발급 성공 시 axiosInstance 내부에서 setAccessToken()이 호출됨
        setAuthenticated(true);
      } catch {
        // refresh_token/device_id도 무효 → 로그인 안 된 상태로 확정
        setAuthenticated(false);
      } finally {
        setChecked(true);
      }
    };

    verifyAuth();
    // location.pathname을 의존성에 넣지 않음
    // → 페이지 이동마다 재검증하지 않고, 마운트 시 한 번만 검증
    // (이미 인증된 이후의 페이지 이동은 메모리 토큰으로 충분)
  }, []);

  // [추가] 인증 확인 중에는 빈 화면(또는 로딩 스피너) 표시
  // - 이 시점에 Navigate를 호출하면 깜빡임/오작동 발생
  if (!checked) {
    return (
        <div className="flex min-h-screen items-center justify-center">
          <div className="text-sm text-[#9e9e9e]">로딩 중...</div>
        </div>
    );
  }

  // 인증 확인 완료 후 - 로그인 안 됨 → /login으로 이동
  if (!authenticated) {
    return (
        <Navigate
            to="/login"
            replace
            state={{ from: location.pathname }}
        />
    );
  }

  // 인증 확인 완료 후 - 로그인됨 → 자식 라우트 렌더링
  return <Outlet />;
}