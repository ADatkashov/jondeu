import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, request, setTokens } from '@/lib/api';

const tokens = (a: string, r: string) => ({ accessToken: a, refreshToken: r, expiresInSeconds: 1800, user: { id: 'u', phone: '+7', fullName: 'x', role: 'CUSTOMER', city: 'Almaty', language: 'ru' } }) as never;
const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });

describe('api client', () => {
  beforeEach(() => { setTokens(tokens('old', 'r1')); });
  afterEach(() => { vi.restoreAllMocks(); setTokens(null); });

  it('refreshes the token once on 401 and retries the request', async () => {
    const f = vi.spyOn(globalThis, 'fetch').mockImplementation(async (url, init) => {
      const u = String(url);
      if (u.endsWith('/auth/refresh')) return json(200, tokens('new', 'r2'));
      const auth = (init?.headers as Record<string, string>).Authorization;
      return auth === 'Bearer new' ? json(200, { ok: true }) : json(401, {});
    });
    await expect(request('/api/v1/projects')).resolves.toEqual({ ok: true });
    expect(f).toHaveBeenCalledTimes(3);
  });

  it('shares a single refresh between concurrent 401s', async () => {
    let refreshes = 0;
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (url, init) => {
      if (String(url).endsWith('/auth/refresh')) { refreshes++; await new Promise((r) => setTimeout(r, 10)); return json(200, tokens('new', 'r2')); }
      return (init?.headers as Record<string, string>).Authorization === 'Bearer new' ? json(200, {}) : json(401, {});
    });
    await Promise.all([request('/a'), request('/b'), request('/c')]);
    expect(refreshes).toBe(1);
  });

  it('turns error bodies into ApiError with code and fields', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(json(400, { code: 'VALIDATION_FAILED', message: 'bad', fields: { name: 'required' } }));
    const e = await request('/x').catch((x) => x);
    expect(e).toBeInstanceOf(ApiError);
    expect(e).toMatchObject({ status: 400, code: 'VALIDATION_FAILED', fields: { name: 'required' } });
  });

  it('reports network failures as ApiError(0)', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('fail'));
    await expect(request('/x')).rejects.toMatchObject({ status: 0, code: 'NETWORK' });
  });
});
