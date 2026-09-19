import { createContext, useCallback, useEffect, useState, type ReactNode } from 'react';
import { apiFetch, ApiError } from '../api/client';
import type { AuthStatus, UserDto } from '../types';

export interface AuthContextType {
  user: UserDto | null;
  loading: boolean;
  error: string | null;
  checkAuth: () => Promise<void>;
  loginWithDev: (email?: string) => Promise<void>;
  logout: () => Promise<void>;
  clearError: () => void;
}

export const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const checkAuth = useCallback(async () => {
    try {
      setLoading(true);
      const data = await apiFetch<AuthStatus>('/api/auth/status');
      if (data.authenticated && data.user) {
        setUser(data.user);
      } else {
        setUser(null);
      }
    } catch (err) {
      setUser(null);
      console.error('Failed to verify session status:', err);
    } finally {
      setLoading(false);
    }
  }, []);

  const loginWithDev = useCallback(async (email = 'test@example.com') => {
    try {
      setLoading(true);
      setError(null);
      await apiFetch(`/api/auth/dev-login?email=${encodeURIComponent(email)}`, {
        method: 'POST',
      });
      await checkAuth();
    } catch (err) {
      if (err instanceof ApiError && err.data && typeof err.data === 'object' && 'message' in (err.data as Record<string, unknown>)) {
        setError(String((err.data as Record<string, unknown>).message));
      } else if (err instanceof ApiError && err.status === 403) {
        setError(`Access Denied: Email '${email}' is not on the authorized whitelist.`);
      } else {
        setError(err instanceof Error ? err.message : 'Dev login failed');
      }
      setLoading(false);
    }
  }, [checkAuth]);

  const logout = useCallback(async () => {
    try {
      setLoading(true);
      await apiFetch('/api/auth/logout', { method: 'POST' });
    } catch (err) {
      console.error('Logout error:', err);
    } finally {
      setUser(null);
      setLoading(false);
    }
  }, []);

  const clearError = useCallback(() => {
    setError(null);
  }, []);

  // On mount, check URL query parameters for auth errors and initialize session
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const authError = params.get('error');

    if (authError === 'unauthorized') {
      setError('Access Denied: Your email address is not on the authorized whitelist.');
      // Clean query parameter from URL without reload
      const cleanUrl = window.location.pathname;
      window.history.replaceState({}, document.title, cleanUrl);
    } else if (authError) {
      setError(`Authentication Error: ${authError}`);
      const cleanUrl = window.location.pathname;
      window.history.replaceState({}, document.title, cleanUrl);
    }

    checkAuth();
  }, [checkAuth]);

  return (
    <AuthContext.Provider
      value={{
        user,
        loading,
        error,
        checkAuth,
        loginWithDev,
        logout,
        clearError,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}
