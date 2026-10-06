import { useState, useEffect, useCallback } from 'react';
import type { AuthResponse, LoginRequest, RegisterRequest, Role } from '../types';
import { authApi } from '../api/client';

export interface UserSession {
  username: string;
  role: Role;
}

export function useAuth() {
  const [token, setToken] = useState<string | null>(() => {
    return localStorage.getItem('auth_token');
  });

  const [user, setUser] = useState<UserSession | null>(() => {
    const raw = localStorage.getItem('auth_user');
    if (!raw) return null;
    try {
      return JSON.parse(raw);
    } catch {
      return null;
    }
  });

  const [loading, setLoading] = useState<boolean>(false);

  const saveAuth = useCallback((data: AuthResponse) => {
    localStorage.setItem('auth_token', data.token);
    const session: UserSession = {
      username: data.username,
      role: data.role,
    };
    localStorage.setItem('auth_user', JSON.stringify(session));
    setToken(data.token);
    setUser(session);
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem('auth_token');
    localStorage.removeItem('auth_user');
    setToken(null);
    setUser(null);
  }, []);

  const login = useCallback(
    async (req: LoginRequest) => {
      setLoading(true);
      try {
        const res = await authApi.login(req);
        saveAuth(res);
      } finally {
        setLoading(false);
      }
    },
    [saveAuth]
  );

  const register = useCallback(
    async (req: RegisterRequest) => {
      setLoading(true);
      try {
        const res = await authApi.register(req);
        saveAuth(res);
      } finally {
        setLoading(false);
      }
    },
    [saveAuth]
  );

  useEffect(() => {
    const handleAuthExpired = () => {
      logout();
    };

    window.addEventListener('auth:expired', handleAuthExpired);
    return () => {
      window.removeEventListener('auth:expired', handleAuthExpired);
    };
  }, [logout]);

  return {
    token,
    user,
    isAuthenticated: !!token,
    loading,
    login,
    register,
    logout,
  };
}
