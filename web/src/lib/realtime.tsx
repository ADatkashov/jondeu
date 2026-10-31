'use client';
import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { API_URL, api, freshAccessToken } from './api';
import { useAuth } from './auth';
import { useProject } from './project';
import { useToast } from './toast';
import type { ProjectEvent } from './types';

export type ConnState = 'connecting' | 'online' | 'offline';
const C = createContext<{ state: ConnState; lastEvent?: ProjectEvent }>({ state: 'offline' });

/** entityType -> TanStack Query keys (prefix of ['x', projectId]) that must be refetched. Overview/activity always refresh. */
const KEYS: Record<string, string[]> = {
  project: ['project', 'projects'], estimate: ['estimates'], stage: ['stages'], payment: ['payments'], material: ['materials'], inspection: ['inspections'],
  media: ['media'], calendar: ['calendar'], proposal: ['proposals', 'projects'], member: ['members'],
};

/**
 * One WebSocket per browser tab. Every server-side domain change arrives as a `project.event`; instead of patching local
 * state (which would duplicate business logic) we invalidate the affected queries and let the backend remain the single source of truth.
 */
export function RealtimeProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const { projectId, projects } = useProject();
  const qc = useQueryClient();
  const toast = useToast();
  const [state, setState] = useState<ConnState>('offline');
  const [lastEvent, setLastEvent] = useState<ProjectEvent>();
  const wsRef = useRef<WebSocket | null>(null);
  const subscribed = useRef<Set<string>>(new Set());
  const seqRef = useRef<Record<string, number>>({});
  const myId = user?.id;

  // (re)connect loop with exponential backoff
  useEffect(() => {
    if (!user) return;
    let closed = false, attempt = 0, timer: ReturnType<typeof setTimeout>;
    const connect = async () => {
      if (closed) return;
      setState('connecting');
      const token = await freshAccessToken();
      if (!token) { setState('offline'); return; }
      const ws = new WebSocket(`${API_URL.replace(/^http/, 'ws')}/ws?token=${encodeURIComponent(token)}`);
      wsRef.current = ws;
      ws.onopen = () => { attempt = 0; setState('online'); subscribed.current.clear(); qc.invalidateQueries(); /* catch-up after (re)connect */ };
      ws.onmessage = (m) => {
        let msg: any; try { msg = JSON.parse(m.data); } catch { return; }
        if (msg.kind === 'project.event') {
          const e = msg as ProjectEvent;
          seqRef.current[e.projectId] = Math.max(seqRef.current[e.projectId] ?? 0, e.seq);
          setLastEvent(e);
          const keys = KEYS[e.entityType] ?? [];
          for (const k of ['overview', 'activity', ...keys]) qc.invalidateQueries({ queryKey: [k, e.projectId] });
          if (keys.includes('projects')) qc.invalidateQueries({ queryKey: ['projects'] });
          if (e.actorId !== myId && e.message) toast(e.message, 'info');
        } else if (msg.kind === 'notification') {
          qc.invalidateQueries({ queryKey: ['notifications'] });
        } else if (msg.kind === 'chat.message' || msg.kind === 'chat.read') {
          qc.invalidateQueries({ queryKey: ['conversations'] });
          qc.invalidateQueries({ queryKey: ['messages', msg.conversationId] });
        }
      };
      ws.onclose = () => {
        setState('offline'); wsRef.current = null;
        if (!closed) { timer = setTimeout(connect, Math.min(15_000, 500 * 2 ** attempt++)); }
      };
      ws.onerror = () => ws.close();
    };
    connect();
    const ping = setInterval(() => { if (wsRef.current?.readyState === 1) wsRef.current.send('{"action":"ping"}'); }, 25_000);
    return () => { closed = true; clearTimeout(timer); clearInterval(ping); wsRef.current?.close(); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user?.id]);

  // subscribe to all visible projects once connected so toasts/refreshes work for any of them
  useEffect(() => {
    const ws = wsRef.current;
    if (state !== 'online' || !ws) return;
    for (const p of projects) {
      if (!subscribed.current.has(p.id)) { ws.send(JSON.stringify({ action: 'subscribe', projectId: p.id })); subscribed.current.add(p.id); }
    }
  }, [state, projects, projectId]);

  // gap fill: after reconnect fetch events we may have missed for the active project (also keeps the activity feed exact)
  useEffect(() => {
    if (state !== 'online' || !projectId) return;
    const after = seqRef.current[projectId] ?? 0;
    if (after > 0) api.get<ProjectEvent[]>(`/api/v1/projects/${projectId}/events?after=${after}`).then((evs) => { if (evs.length) qc.invalidateQueries(); }).catch(() => {});
  }, [state, projectId, qc]);

  return <C.Provider value={{ state, lastEvent }}>{children}</C.Provider>;
}
export const useRealtime = () => useContext(C);
