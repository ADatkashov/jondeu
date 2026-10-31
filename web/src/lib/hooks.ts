'use client';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from './api';
import { useProject } from './project';
import { useToast } from './toast';
import { useT } from './i18n';
import type { CalendarItem, Category, Estimate, Inspection, Material, MediaItem, Member, Overview, Payment, Proposal, Stage } from './types';

/** Project-scoped query: key = [name, projectId] so realtime invalidation can target it. */
export function useProjectQuery<T>(name: string, path: (id: string) => string, extra: unknown[] = []) {
  const { projectId } = useProject();
  return useQuery<T>({ queryKey: [name, projectId, ...extra], queryFn: () => api.get<T>(path(projectId!)), enabled: !!projectId });
}

export const useOverview = () => useProjectQuery<Overview>('overview', (id) => `/api/v1/projects/${id}/overview`);
export const useStages = () => useProjectQuery<Stage[]>('stages', (id) => `/api/v1/projects/${id}/stages`);
export const useEstimates = () => useProjectQuery<Estimate[]>('estimates', (id) => `/api/v1/projects/${id}/estimates`);
export const usePayments = () => useProjectQuery<Payment[]>('payments', (id) => `/api/v1/projects/${id}/payments`);
export const useMaterials = () => useProjectQuery<{ items: Material[]; summary: { plannedCost: number; actualCost: number; variance: number; total: number; delivered: number } }>('materials', (id) => `/api/v1/projects/${id}/materials`);
export const useInspections = () => useProjectQuery<Inspection[]>('inspections', (id) => `/api/v1/projects/${id}/inspections`);
export const useMembers = () => useProjectQuery<Member[]>('members', (id) => `/api/v1/projects/${id}/members`);
export const useActivity = (limit = 12) => useProjectQuery<{ seq: number; type: string; message?: string; createdAt: string; actorId?: string }[]>('activity', (id) => `/api/v1/projects/${id}/activity?limit=${limit}`, [limit]);
export const useProposals = () => useProjectQuery<Proposal[]>('proposals', (id) => `/api/v1/projects/${id}/proposals`);
export function useMedia(filters: Record<string, string> = {}) {
  const qs = new URLSearchParams(Object.entries(filters).filter(([, v]) => v)).toString();
  return useProjectQuery<MediaItem[]>('media', (id) => `/api/v1/projects/${id}/media${qs ? `?${qs}` : ''}`, [qs]);
}
export const useCalendar = (from: string, to: string) => useProjectQuery<CalendarItem[]>('calendar', (id) => `/api/v1/projects/${id}/calendar?from=${from}&to=${to}`, [from, to]);
export const useCategories = () => useQuery({ queryKey: ['categories'], queryFn: () => api.get<Category[]>('/api/v1/catalog/categories'), staleTime: Infinity });

/** Mutation helper: toast on success/error, then refetch project data (realtime will do the same for other clients). */
export function useAction<TVars, TRes = unknown>(fn: (v: TVars) => Promise<TRes>, opts: { success?: string; invalidate?: string[]; onDone?: (r: TRes) => void } = {}) {
  const qc = useQueryClient();
  const toast = useToast();
  const t = useT();
  const { projectId } = useProject();
  return useMutation({
    mutationFn: fn,
    onSuccess: (r) => {
      if (opts.success) toast(t(opts.success), 'success');
      for (const k of ['overview', 'activity', ...(opts.invalidate ?? [])]) qc.invalidateQueries({ queryKey: [k, projectId] });
      opts.onDone?.(r);
    },
    onError: (e) => toast(e instanceof ApiError ? errorText(e, t) : t('common.error'), 'error'),
  });
}

export function errorText(e: ApiError, t: (k: string) => string): string {
  const localized = t(`error.${e.code}`);
  return localized !== `error.${e.code}` ? localized : e.message;
}
