import { useSyncExternalStore } from 'react';

export type UserStatus = 'ACTIVE' | 'SUSPENDED' | 'WITHDRAWN';

interface AuthStatusState {
  userStatus: UserStatus | null;
  isSuspended: boolean;
}

let state: AuthStatusState = {
  userStatus: null,
  isSuspended: false,
};

const listeners = new Set<() => void>();

const emit = () => {
  listeners.forEach((listener) => listener());
};

export const getAuthStatusState = () => state;

export const subscribeAuthStatus = (listener: () => void) => {
  listeners.add(listener);
  return () => listeners.delete(listener);
};

export const setUserStatus = (userStatus: UserStatus | null) => {
  state = {
    userStatus,
    isSuspended: userStatus === 'SUSPENDED',
  };
  emit();
};

export const clearAuthStatus = () => {
  state = {
    userStatus: null,
    isSuspended: false,
  };
  emit();
};

export const useAuthStatus = () =>
  useSyncExternalStore(subscribeAuthStatus, getAuthStatusState, getAuthStatusState);

export const isSuspendedAllowedPath = (pathname: string) =>
  pathname === '/me' ||
  pathname.startsWith('/me/support') ||
  pathname.startsWith('/me/inquiries') ||
  pathname === '/app/me' ||
  pathname.startsWith('/app/me/support') ||
  pathname.startsWith('/app/me/inquiries');
