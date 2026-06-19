import { useEffect, useState } from 'react';
import { RouterProvider } from 'react-router';
import { refresh } from '@/api/authApi';
import { clearAccessToken, hasLoginRestoreHint, setAccessToken } from '@/api/axiosInstance';
import { getUserMe } from '@/api/userApi';
import { setUserStatus } from '@/store/authStatusStore';
import HankkiLoadingScreen from './components/HankkiLoadingScreen';
import { Toaster } from './components/ui/sonner';
import { router } from './routes';

const MIN_LOADING_MS = 650;

export default function App() {
  const [authChecked, setAuthChecked] = useState(false);

  useEffect(() => {
    const startedAt = performance.now();
    let isMounted = true;
    let finishTimer: number | undefined;
    const finishAuthCheck = () => {
      const remaining = Math.max(0, MIN_LOADING_MS - (performance.now() - startedAt));
      finishTimer = window.setTimeout(() => {
        if (isMounted) {
          setAuthChecked(true);
        }
      }, remaining);
    };

    const restoreLogin = async () => {
      if (window.location.pathname.startsWith('/admin')) {
        finishAuthCheck();
        return;
      }

      if (!hasLoginRestoreHint()) {
        clearAccessToken();
        finishAuthCheck();
        return;
      }

      try {
        const res = await refresh();
        setAccessToken(res.data.data.accessToken);
        const meRes = await getUserMe();
        setUserStatus(meRes.data.data.status);
      } catch {
        clearAccessToken();
      } finally {
        finishAuthCheck();
      }
    };

    restoreLogin();

    return () => {
      isMounted = false;
      if (finishTimer) {
        window.clearTimeout(finishTimer);
      }
    };
  }, []);

  if (!authChecked) {
    return <HankkiLoadingScreen label="한끼팟 준비 중" />;
  }

  return (
    <>
      <RouterProvider router={router} />
      <Toaster position="top-center" richColors />
    </>
  );
}
