'use client';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus } from 'lucide-react';
import { useState } from 'react';
import { StatusBadge } from '@/components/Status';
import { Badge, Button, Card, Dialog, DialogContent, EmptyState, ErrorState, PageHeader, Skeleton } from '@/components/ui';
import { ProjectWizard } from '@/features/ProjectWizard';
import { api, ApiError } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { fmtMoney } from '@/lib/format';
import { errorText } from '@/lib/hooks';
import { useI18n } from '@/lib/i18n';
import { useProject } from '@/lib/project';
import { useToast } from '@/lib/toast';
import type { Project, Proposal } from '@/lib/types';

function Proposals({ project, onClose }: { project: Project; onClose: () => void }) {
  const { t } = useI18n();
  const qc = useQueryClient();
  const toast = useToast();
  const q = useQuery({ queryKey: ['proposals', project.id], queryFn: () => api.get<Proposal[]>(`/api/v1/projects/${project.id}/proposals`) });
  const act = useMutation({
    mutationFn: ({ id, kind }: { id: string; kind: 'accept' | 'reject' }) => api.post(`/api/v1/proposals/${id}/${kind}`),
    onSuccess: (_, v) => { toast(t(v.kind === 'accept' ? 'proposals.accepted' : 'proposals.rejected'), 'success'); qc.invalidateQueries(); if (v.kind === 'accept') onClose(); },
    onError: (e) => toast(e instanceof ApiError ? errorText(e, t) : t('common.error'), 'error'),
  });
  const cheapest = Math.min(...(q.data ?? []).map((p) => p.price));
  return (
    <DialogContent title={`${t('proposals.title')}: ${project.name}`} wide>
      {q.isLoading ? <Skeleton className="h-32" /> : q.isError ? <ErrorState error={q.error} /> : !q.data?.length ? <EmptyState title={t('proposals.empty')} /> : (
        <div className="overflow-x-auto"><table className="w-full text-sm"><thead><tr className="text-left text-xs text-ink-soft"><th className="py-2">{t('proposals.master')}</th><th>{t('proposals.price')}</th><th>{t('proposals.days')}</th><th>{t('proposals.message')}</th><th /></tr></thead>
          <tbody className="divide-y divide-line">{q.data.map((p) => (
            <tr key={p.id} className="align-top"><td className="py-3 font-medium">{p.masterName}<div><StatusBadge status={p.status} /></div></td>
              <td className="tabular py-3 font-semibold">{fmtMoney(p.price)}{p.price === cheapest && q.data!.length > 1 && <Badge tone="green" className="ml-2">{t('proposals.best')}</Badge>}</td>
              <td className="py-3">{p.durationDays}</td><td className="max-w-[260px] py-3 text-ink-soft">{p.message}</td>
              <td className="py-3 text-right">{p.status === 'PENDING' && project.myRole === 'OWNER' && (
                <div className="flex justify-end gap-2"><Button size="sm" variant="success" loading={act.isPending} onClick={() => act.mutate({ id: p.id, kind: 'accept' })}>{t('proposals.accept')}</Button>
                  <Button size="sm" variant="outline" onClick={() => act.mutate({ id: p.id, kind: 'reject' })}>{t('proposals.reject')}</Button></div>)}</td></tr>
          ))}</tbody></table></div>
      )}
    </DialogContent>
  );
}

export default function ProjectsPage() {
  const { t } = useI18n();
  const { projects, loading, select, project: current } = useProject();
  const { isAdmin } = useAuth();
  const qc = useQueryClient();
  const toast = useToast();
  const [wizard, setWizard] = useState(false);
  const [viewing, setViewing] = useState<Project | null>(null);
  const run = useMutation({
    mutationFn: ({ id, action }: { id: string; action: string }) => api.post(`/api/v1/projects/${id}/${action}`),
    onSuccess: () => { toast(t('common.done'), 'success'); qc.invalidateQueries(); },
    onError: (e) => toast(e instanceof ApiError ? errorText(e, t) : t('common.error'), 'error'),
  });
  return (
    <>
      <PageHeader title={t('nav.projects')} actions={!isAdmin && <Dialog open={wizard} onOpenChange={setWizard}><Button onClick={() => setWizard(true)}><Plus className="h-4 w-4" />{t('projects.create')}</Button>{wizard && <ProjectWizard onClose={() => setWizard(false)} />}</Dialog>} />
      {loading ? <Skeleton className="h-40" /> : !projects.length ? <EmptyState title={t('projects.empty')} text={t('projects.emptyHint')} /> : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {projects.map((p) => (
            <Card key={p.id} className={p.id === current?.id ? 'ring-2 ring-brand/40' : ''}>
              <div className="mb-2 flex items-start justify-between gap-2"><h3 className="font-semibold">{p.name}</h3><StatusBadge status={p.status} /></div>
              <p className="text-sm text-ink-soft">{p.address ?? p.city} · {p.areaM2} m²</p>
              <p className="mt-2 text-sm">{p.approvedBudget > 0 ? <><span className="text-ink-soft">{t('budget.approved')}: </span><b className="tabular">{fmtMoney(p.approvedBudget)}</b></> : p.budgetMax ? <span className="text-ink-soft">{t('projects.budgetRange')}: {fmtMoney(p.budgetMin)} – {fmtMoney(p.budgetMax)}</span> : null}</p>
              {p.contractorName && <p className="mt-1 text-xs text-ink-soft">{t('projects.contractor')}: {p.contractorName}</p>}
              <div className="mt-4 flex flex-wrap gap-2">
                <Button size="sm" variant="outline" onClick={() => select(p.id)}>{t('projects.open')}</Button>
                {p.myRole === 'OWNER' && p.status === 'DRAFT' && <Button size="sm" onClick={() => run.mutate({ id: p.id, action: 'submit' })}>{t('wizard.submit')}</Button>}
                {p.myRole === 'OWNER' && ['SUBMITTED', 'UNDER_REVIEW', 'PROPOSALS_RECEIVED'].includes(p.status) && <Button size="sm" variant="accent" onClick={() => setViewing(p)}>{t('proposals.compare')}</Button>}
                {p.myRole === 'OWNER' && p.status === 'IN_PROGRESS' && <Button size="sm" variant="success" onClick={() => confirm(t('projects.confirmComplete')) && run.mutate({ id: p.id, action: 'complete' })}>{t('projects.complete')}</Button>}
                {p.myRole === 'OWNER' && ['COMPLETED', 'CANCELLED'].includes(p.status) && !p.archived && <Button size="sm" variant="ghost" onClick={() => run.mutate({ id: p.id, action: 'archive' })}>{t('projects.archive')}</Button>}
                {p.myRole === 'OWNER' && !['COMPLETED', 'CANCELLED'].includes(p.status) && <Button size="sm" variant="ghost" onClick={() => confirm(t('projects.confirmCancel')) && run.mutate({ id: p.id, action: 'cancel' })}>{t('common.cancel')}</Button>}
              </div>
            </Card>
          ))}
        </div>
      )}
      <Dialog open={!!viewing} onOpenChange={(o) => !o && setViewing(null)}>{viewing && <Proposals project={viewing} onClose={() => setViewing(null)} />}</Dialog>
    </>
  );
}
