'use client';
import { useQuery } from '@tanstack/react-query';
import { BadgeCheck } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useEffect } from 'react';
import { StatusBadge } from '@/components/Status';
import { Badge, Button, Card, CardTitle, EmptyState, ErrorState, PageHeader, Skeleton, StatCard } from '@/components/ui';
import { api } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { fmtDateTime, fmtMoney } from '@/lib/format';
import { useI18n } from '@/lib/i18n';
import { useToast } from '@/lib/toast';
import type { Master, Page, Payment, User } from '@/lib/types';

export default function AdminPage() {
  const { t, locale } = useI18n();
  const { isAdmin, user } = useAuth();
  const router = useRouter();
  const toast = useToast();
  useEffect(() => { if (user && !isAdmin) router.replace('/dashboard'); }, [user, isAdmin, router]);
  const stats = useQuery({ queryKey: ['admin', 'stats'], queryFn: () => api.get<{ users: number; projects: number; payments: number; projectsByStatus: Record<string, number> }>('/api/v1/admin/stats'), enabled: isAdmin });
  const users = useQuery({ queryKey: ['admin', 'users'], queryFn: () => api.get<Page<User>>('/api/v1/admin/users?size=100'), enabled: isAdmin });
  const masters = useQuery({ queryKey: ['admin', 'masters'], queryFn: () => api.get<Page<Master>>('/api/v1/masters?size=100'), enabled: isAdmin });
  const pays = useQuery({ queryKey: ['admin', 'payments'], queryFn: () => api.get<Page<Payment>>('/api/v1/admin/payments?size=20'), enabled: isAdmin });
  const audit = useQuery({ queryKey: ['admin', 'audit'], queryFn: () => api.get<{ id: number; action: string; detail?: string; createdAt: string }[]>('/api/v1/admin/audit?size=30'), enabled: isAdmin });
  const verify = async (m: Master) => { try { await api.post(`/api/v1/admin/masters/${m.id}/verify`, { verified: !m.verified }); toast(t('common.done'), 'success'); masters.refetch(); audit.refetch(); } catch { toast(t('common.error'), 'error'); } };
  const toggle = async (u: User, enabled: boolean) => { try { await api.post(`/api/v1/admin/users/${u.id}/enabled`, { enabled }); toast(t('common.done'), 'success'); users.refetch(); audit.refetch(); } catch { toast(t('common.error'), 'error'); } };
  if (!isAdmin) return <EmptyState title={t('admin.forbidden')} />;
  return (
    <>
      <PageHeader title={t('nav.admin')} />
      {stats.data && <div className="mb-4 grid grid-cols-2 gap-3 md:grid-cols-4"><StatCard label={t('admin.users')} value={stats.data.users} /><StatCard label={t('nav.projects')} value={stats.data.projects} /><StatCard label={t('nav.payments')} value={stats.data.payments} />
        <StatCard label={t('status.IN_PROGRESS')} value={stats.data.projectsByStatus.IN_PROGRESS ?? 0} /></div>}
      <div className="grid gap-4 xl:grid-cols-2">
        <Card><CardTitle>{t('admin.verification')}</CardTitle>{masters.isLoading ? <Skeleton className="h-32" /> : masters.isError ? <ErrorState error={masters.error} /> : (
          <ul className="divide-y divide-line">{masters.data?.content.map((m) => <li key={m.id} className="flex items-center justify-between gap-2 py-2 text-sm"><span>{m.fullName} <span className="text-ink-soft">· {m.specialization}</span></span>
            <Button size="sm" variant={m.verified ? 'outline' : 'primary'} onClick={() => verify(m)}>{m.verified ? <><BadgeCheck className="h-4 w-4" />{t('admin.unverify')}</> : t('admin.verify')}</Button></li>)}</ul>)}</Card>
        <Card><CardTitle>{t('admin.users')}</CardTitle>{users.isLoading ? <Skeleton className="h-32" /> : (
          <ul className="max-h-96 divide-y divide-line overflow-y-auto">{users.data?.content.map((u) => <li key={u.id} className="flex items-center justify-between gap-2 py-2 text-sm"><span>{u.fullName} <Badge>{t(`role.${u.role}`)}</Badge></span>
            {u.id !== user?.id && <Button size="sm" variant="ghost" onClick={() => toggle(u, false)}>{t('admin.disable')}</Button>}</li>)}</ul>)}</Card>
        <Card><CardTitle>{t('admin.payments')}</CardTitle><ul className="divide-y divide-line">{pays.data?.content.map((p) => <li key={p.id} className="flex items-center justify-between py-2 text-sm"><span>{p.reference} <span className="text-ink-soft">{fmtMoney(p.amount)}</span></span><StatusBadge status={p.status} /></li>)}</ul></Card>
        <Card><CardTitle>{t('admin.audit')}</CardTitle><ul className="max-h-80 divide-y divide-line overflow-y-auto">{audit.data?.map((a) => <li key={a.id} className="py-2 text-sm"><b>{a.action}</b> <span className="text-ink-soft">{a.detail}</span><div className="text-xs text-ink-soft">{fmtDateTime(a.createdAt, locale)}</div></li>)}
          {!audit.data?.length && <li className="py-4 text-center text-sm text-ink-soft">{t('activity.empty')}</li>}</ul></Card>
      </div>
    </>
  );
}
