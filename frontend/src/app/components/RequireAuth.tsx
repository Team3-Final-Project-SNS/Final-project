import { Navigate, Outlet, useLocation } from 'react-router';
import { getAccessToken } from '@/api/axiosInstance';

export default function RequireAuth() {
  const location = useLocation();

  if (!getAccessToken()) {
    return (
      <Navigate
        to="/login"
        replace
        state={{ from: location.pathname }}
      />
    );
  }

  return <Outlet />;
}
