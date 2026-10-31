'use client';
import { Plus } from 'lucide-react';
import { useState } from 'react';
import { StatusBadge } from '@/components/Status';
import { Button, Card, Dialog, DialogContent, EmptyState, ErrorState, Field, Input, PageHeader, Select, Skeleton, Textarea } from '@/components/ui';
import { api } from '@/lib/api';
import { fmtDate } from '@/lib/format';
import { useAction, useInspections, useStages } from '@/lib/hooks';
import { useI18n } from '@/lib/i18n';
import { useProject } from '@/lib/project';
import type { Inspection, InspectionResult } from '@/lib/types';

const RESULTS: InspectionResult[] = ['PASSED', 'NEEDS_IMPROVEMENT', 'FAILED', 'PENDING'];

function InspectionForm({ existing, onClose }: { existing?: Inspection; onClose: () => void }) {
  const { t } = useI18n();
  const { projectId } = useProject();
  const stages = useStages();
  const [v, setV] = useState({ title: existing?.title ?? '', stageId: existing?.stageId ?? '', inspectionDate: existing?.inspectionDate ?? new Date().toISOString().slice(0, 10), result: existing?.result ?? 'PENDING', comments: existing?.comments ?? '', correctiveActions: existing?.correctiveActions ?? '' });
  const body = { ...v, stageId: v.stageId || undefined };
  const save = useAction(() => (existing ? api.patch(`/api/v1/inspections/${existing.id}`, body) : api.post(`/api/v1/projects/${projectId}/inspections`, body)), { success: 'quality.saved', invalidate: ['inspections'], onDone: onClose });
  return (
    <DialogContent title={existing ? t('quality.edit') : t('quality.new')}>
      <div className="grid gap-3 sm:grid-cols-2">
        <div className="sm:col-span-2"><Field label={t('quality.titleField')}><Input value={v.title} onChange={(e) => setV({ ...v, title: e.target.value })} /></Field></div>
        <Field label={t('photos.stage')}><Select value={v.stageId} onChange={(e) => setV({ ...v, stageId: e.target.value })}><option value="">—</option>{stages.data?.map((s) => <option key={s.id} value={s.id}>{s.position}. {s.title}</option>)}</Select></Field>
        <Field label={t('quality.date')}><Input type="date" value={v.inspectionDate} onChange={(e) => setV({ ...v, inspectionDate: e.target.value })} /></Field>
        <div className="sm:col-span-2"><Field label={t('quality.result')}><Select value={v.result} onChange={(e) => setV({ ...v, result: e.target.value as InspectionResult })}>{RESULTS.map((r) => <option key={r} value={r}>{t(`status.${r}`)}</option>)}</Select></Field></div>
        <div className="sm:col-span-2"><Field label={t('quality.comments')}><Textarea value={v.comments} onChange={(e) => setV({ ...v, comments: e.target.value })} /></Field></div>
        <div className="sm:col-span-2"><Field label={t('quality.actions')}><Textarea value={v.correctiveActions} onChange={(e) => setV({ ...v, correctiveActions: e.target.value })} /></Field></div>
      </div>
      <div className="mt-4 flex justify-end gap-2"><Button variant="ghost" onClick={onClose}>{t('common.cancel')}</Button><Button disabled={v.title.trim().length < 2} loading={save.isPending} onClick={() => save.mutate(undefined)}>{t('common.save')}</Button></div>
    </DialogContent>
  );
}

export default function QualityPage() {
  const { t, locale } = useI18n();
  const { project, projectId } = useProject();
  const q = useInspections();
  const stages = useStages();
  const [form, setForm] = useState<{ ins?: Inspection } | null>(null);
  const canInspect = ['INSPECTOR', 'ADMIN'].includes(project?.myRole ?? '');
  if (!projectId) return <EmptyState title={t('dash.noProject')} />;
  const stageName = (id?: string) => stages.data?.find((s) => s.id === id)?.title;
  return (
    <>
      <PageHeader title={t('nav.quality')} subtitle={t('quality.subtitle')} actions={canInspect && <Button onClick={() => setForm({})}><Plus className="h-4 w-4" />{t('quality.new')}</Button>} />
      {q.isLoading ? <Skeleton className="h-48" /> : q.isError ? <ErrorState error={q.error} onRetry={() => q.refetch()} /> : !q.data?.length ? <EmptyState title={t('quality.empty')} text={t('quality.emptyHint')} /> : (
        <div className="space-y-3">{q.data.map((i) => (
          <Card key={i.id} className="p-4"><div className="flex flex-wrap items-start justify-between gap-2"><div><h3 className="font-semibold">{i.title}</h3><p className="text-xs text-ink-soft">{fmtDate(i.inspectionDate, locale)}{stageName(i.stageId) && ` · ${stageName(i.stageId)}`}</p></div>
            <div className="flex items-center gap-2"><StatusBadge status={i.result} />{canInspect && <Button size="sm" variant="outline" onClick={() => setForm({ ins: i })}>{t('common.edit')}</Button>}</div></div>
            {i.comments && <p className="mt-2 text-sm">{i.comments}</p>}
            {i.correctiveActions && <p className="mt-2 rounded-control bg-warning-50 p-3 text-sm"><b>{t('quality.actions')}:</b> {i.correctiveActions}</p>}</Card>
        ))}</div>
      )}
      <Dialog open={!!form} onOpenChange={(o) => !o && setForm(null)}>{form && <InspectionForm existing={form.ins} onClose={() => setForm(null)} />}</Dialog>
    </>
  );
}
