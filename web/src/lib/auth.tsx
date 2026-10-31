'use client';
import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { api, getTokens, onForcedLogout, request, setTokens } from './api';
import type { Role, Tokens, User } from './types';

interface AuthState { user: User | null; ready: boolean; login: (phone: string, password: string) => Promise<void>; register: (p: RegisterInput) => Promise<void>; logout: () => Promise<void>; isAdmin: boolean; isPro: boolean }
export interface RegisterInput { phone: string; password: string; fullName: string; role: Role }

const Ctx = createContext<AuthState>(null as unknown as AuthState);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [ready, setReady] = useState(false);
  const qc = useQueryClient();

  useEffect(() => {
    const t = getTokens();
    if (!t) { setReady(true); return; }
    api.get<User>('/api/v1/auth/me').then(setUser).catch(() => setTokens(null)).finally(() => setReady(true));
    return onForcedLogout(() => { setUser(null); qc.clear(); });
  }, [qc]);

  const accept = useCallback((t: Tokens) => { setTokens(t); qc.clear(); setUser(t.user); }, [qc]);
  const login = useCallback(async (phone: string, password: string) => { accept(await request<Tokens>('/api/v1/auth/login', { method: 'POST', json: { phone, password } })); }, [accept]);
  const register = useCallback(async (p: RegisterInput) => { accept(await request<Tokens>('/api/v1/auth/register', { method: 'POST', json: p })); }, [accept]);
  const logout = useCallback(async () => {
    const t = getTokens();
    try { if (t) await api.post('/api/v1/auth/logout', { refreshToken: t.refreshToken }); } catch { /* best effort */ }
    setTokens(null); qc.clear(); setUser(null);
  }, [qc]);

  const isAdmin = user?.role === 'PLATFORM_ADMIN' || user?.role === 'PLATFORM_SUPER_ADMIN';
  const isPro = !!user && ['MASTER', 'CONTRACTOR', 'COMPANY_ADMIN', 'PROJECT_MANAGER'].includes(user.role);
  return <Ctx.Provider value={{ user, ready, login, register, logout, isAdmin, isPro }}>{children}</Ctx.Provider>;
}

export const useAuth = () => useContext(Ctx);
