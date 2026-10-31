import type { Tokens } from './types';

export const API_URL = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';
const STORE = 'jondeu.tokens';

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string, public fields?: Record<string, string>) { super(message); }
}

type Listener = () => void;
let tokens: Tokens | null = null;
let refreshing: Promise<boolean> | null = null;
const logoutListeners = new Set<Listener>();

function load(): Tokens | null {
  if (tokens) return tokens;
  if (typeof window === 'undefined') return null;
  try { tokens = JSON.parse(localStorage.getItem(STORE) ?? 'null'); } catch { tokens = null; }
  return tokens;
}
export function setTokens(t: Tokens | null) {
  tokens = t;
  if (typeof window === 'undefined') return;
  try { t ? localStorage.setItem(STORE, JSON.stringify(t)) : localStorage.removeItem(STORE); } catch { /* storage blocked: session stays in memory */ }
}
export function getTokens() { return load(); }
export function onForcedLogout(fn: Listener) { logoutListeners.add(fn); return () => { logoutListeners.delete(fn); }; }

async function doRefresh(): Promise<boolean> {
  const t = load();
  if (!t) return false;
  refreshing ??= (async () => {
    try {
      const res = await fetch(`${API_URL}/api/v1/auth/refresh`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken: t.refreshToken }) });
      if (!res.ok) { setTokens(null); logoutListeners.forEach((l) => l()); return false; }
      setTokens(await res.json());
      return true;
    } catch { return false; } finally { refreshing = null; }
  })();
  return refreshing;
}

/** Returns a currently valid access token (refreshing when it is about to expire). Used by the WebSocket. */
export async function freshAccessToken(): Promise<string | null> {
  const t = load();
  if (!t) return null;
  try {
    const exp = JSON.parse(atob(t.accessToken.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))).exp * 1000;
    if (exp - Date.now() < 60_000) await doRefresh();
  } catch { await doRefresh(); }
  return load()?.accessToken ?? null;
}

export async function request<T>(path: string, init: RequestInit & { json?: unknown; headers?: Record<string, string> } = {}, retry = true): Promise<T> {
  const headers: Record<string, string> = { ...(init.headers ?? {}) };
  const t = load();
  if (t) headers.Authorization = `Bearer ${t.accessToken}`;
  let body = init.body;
  if (init.json !== undefined) { headers['Content-Type'] = 'application/json'; body = JSON.stringify(init.json); }
  let res: Response;
  try { res = await fetch(`${API_URL}${path}`, { ...init, headers, body }); }
  catch { throw new ApiError(0, 'NETWORK', 'Нет соединения с сервером'); }
  if (res.status === 401 && retry && t && !path.startsWith('/api/v1/auth/')) {
    // Another request may have already rotated the tokens while this one was in flight: just retry with the fresh token.
    if (load()?.accessToken !== t.accessToken || (await doRefresh())) return request<T>(path, init, false);
  }
  const ct = res.headers.get('content-type') ?? '';
  if (!res.ok) {
    const b = ct.includes('json') ? await res.json().catch(() => ({})) : {};
    throw new ApiError(res.status, b.code ?? 'ERROR', b.message ?? `Ошибка ${res.status}`, b.fields);
  }
  if (res.status === 204) return undefined as T;
  return (ct.includes('json') ? res.json() : res.blob()) as Promise<T>;
}

export const api = {
  get: <T,>(p: string) => request<T>(p),
  post: <T,>(p: string, json?: unknown, headers?: Record<string, string>) => request<T>(p, { method: 'POST', json: json ?? {}, headers }),
  put: <T,>(p: string, json: unknown) => request<T>(p, { method: 'PUT', json }),
  patch: <T,>(p: string, json: unknown) => request<T>(p, { method: 'PATCH', json }),
  del: <T,>(p: string) => request<T>(p, { method: 'DELETE' }),
  upload: <T,>(p: string, file: File, fields: Record<string, string> = {}) => {
    const fd = new FormData();
    fd.append('file', file);
    Object.entries(fields).forEach(([k, v]) => v && fd.append(k, v));
    return request<T>(p, { method: 'POST', body: fd });
  },
};

export function absoluteUrl(path: string) { return path.startsWith('http') ? path : `${API_URL}${path}`; }
