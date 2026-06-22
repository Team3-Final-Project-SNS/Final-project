import { useEffect, useState } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router';
import { getAccessToken } from '@/api/axiosInstance';
import { getUserMe } from '@/api/userApi';
import HankkiLoadingScreen from './HankkiLoadingScreen';

const MIN_LOADING_MS = 650;

export default function RequireAuth() {
  const location = useLocation();
  const [checked, setChecked] = useState(false);
  const [authenticated, setAuthenticated] = useState(false);

  useEffect(() => {
    const startedAt = performance.now();
    let isMounted = true;
    let finishTimer: number | undefined;

    const finishAuthCheck = () => {
      const remaining = Math.max(0, MIN_LOADING_MS - (performance.now() - startedAt));
      finishTimer = window.setTimeout(() => {
        if (isMounted) {
          setChecked(true);
        }
      }, remaining);
    };

    const verifyAuth = async () => {
      if (getAccessToken()) {
        if (isMounted) {
          setAuthenticated(true);
        }
        finishAuthCheck();
        return;
      }

      try {
        await getUserMe();
        if (isMounted) {
          setAuthenticated(true);
        }
      } catch {
        if (isMounted) {
          setAuthenticated(false);
        }
      } finally {
        finishAuthCheck();
      }
    };

    verifyAuth();

    return () => {
      isMounted = false;
      if (finishTimer) {
        window.clearTimeout(finishTimer);
      }
    };
  }, []);

  if (!checked) {
    return <HankkiLoadingScreen label="로그인 상태 확인 중" />;
  }

  if (!authenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  return <Outlet />;
}
