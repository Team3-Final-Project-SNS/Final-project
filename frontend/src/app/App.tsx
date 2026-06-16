import { useEffect, useState } from 'react';
import { RouterProvider } from 'react-router';
import { refresh } from '@/api/authApi';
import { clearAccessToken, setAccessToken } from '@/api/axiosInstance';
import { getUserMe } from '@/api/userApi';
import { setUserStatus } from '@/store/authStatusStore';
import { Toaster } from './components/ui/sonner';
import { router } from './routes';

export default function App() {
  const [authChecked, setAuthChecked] = useState(false);

  useEffect(() => {
    const restoreLogin = async () => {
      if (window.location.pathname.startsWith('/admin')) {
        setAuthChecked(true);
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
        setAuthChecked(true);
      }
    };

    restoreLogin();
  }, []);

  if (!authChecked) {
    return null;
  }

  return (
    <>
      <RouterProvider router={router} />
      <Toaster position="top-center" richColors />
    </>
  );
}
