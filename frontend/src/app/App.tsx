import { useEffect, useState } from 'react';
import { RouterProvider } from 'react-router';
import { refresh } from '@/api/authApi';
import { clearAccessToken, setAccessToken } from '@/api/axiosInstance';
import { router } from './routes';

export default function App() {
  const [authChecked, setAuthChecked] = useState(false);

  useEffect(() => {
    const restoreLogin = async () => {
      try {
        const res = await refresh();
        setAccessToken(res.data.data.accessToken);
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

  return <RouterProvider router={router} />;
}
