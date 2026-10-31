'use client';
import { MessageSquare, Plus } from 'lucide-react';
import { useState } from 'react';
import { StatusBadge } from '@/components/Status';
import { Badge, Button, Card, Dialog, DialogContent, EmptyState, ErrorState, Field, Input, PageHeader, Progress, Skeleton, Textarea } from '@/components/ui';
import { api } from '@/lib/api';
import { fmtDate, fmtMoney } from '@/lib/format';
import { useAction, useStages } from '@/lib/hooks';
import { useI18n } from '@/lib/i18n';
import { useProject } from '@/lib/project';
import type { Stage, StageStatus } from '@/lib/types';
import { useQuery } from '@tanstack/react-query';

function Comments({ stage, onClose }: { stage: Stage; onClose: () => void }) {
  const { t, locale } = useI18n();
  const [text, setText] = useState('');
  const list = useQuery({ queryKey: ['stage-comments', stage.id], queryFn: () => api.get<{ id: string; body: string; createdAt: string }[]>(`/api/v1/stages/${stage.id}/comments`) });
  const add = useAction((b: string) => api.post(`/api/v1/stages/${stage.id}/comments`, { body: b }), { onDone: () => { setText(''); list.refetch(); } });
  return (
    <DialogContent title={`${stage.title}: ${t('stages.comments')}`}>
      <ul className="mb-3 max-h-64 space-y-2 overflow-y-auto">{(list.data ?? []).map((c) => <li key={c.id} className="rounded-control bg-canvas p-3 text-sm">{c.body}<div className="text-xs text-ink-soft">{new Date(c.createdAt).toLocaleString(locale)}</div></li>)}
        {!list.data?.length && <li className="text-sm text-ink-soft">{t('stages.noComments')}</li>}</ul>
      <Textarea value={text} onChange={(e) => setText(e.target.value)} aria-label={t('stages.comments')} />
      <div className="mt-3 flex justify-end gap-2"><Button variant="ghost" onClick={onClose}>{t('common.close')}</Button><Button disabled={!text.trim()} loading={add.isPending} onClick={() => add.mutate(text)}>{t('common.send')}</Button></div>
    </DialogContent>
  );
}

function Timeline({ stages }: { stages: Stage[] }) {
  const dated = stages.filter((s) => s.startDate && s.endDate);
  if (!dated.length) return null;
  const t0 = Math.min(...dated.map((s) => +new Date(s.startDate!)));
  const t1 = Math.max(...dated.map((s) => +new Date(s.endDate!))) + 86400000;
  const span = t1 - t0;
  const today = ((Date.now() - t0) / span) * 100;
  return (
    <div className="relative overflow-x-auto" aria-hidden>
      <div className="relative min-w-[560px] space-y-1.5 py-1">
        {dated.map((s) => {
          const left = ((+new Date(s.startDate!) - t0) / span) * 100, width = ((+new Date(s.endDate!) + 86400000 - +new Date(s.startDate!)) / span) * 100;
          const color = s.status === 'COMPLETED' ? 'bg-success' : s.status === 'DELAYED' ? 'bg-accent' : s.status === 'BLOCKED' ? 'bg-danger' : 'bg-brand';
          return <div key={s.id} className="relative h-6"><div className={`absolute h-6 overflow-hidden rounded-md ${color}/25`} style={{ left: `${left}%`, width: `${width}%` }}><div className={`h-full ${color}`} style={{ width: `${s.progress}%` }} />
            <span className="absolute inset-0 flex items-center truncate px-2 text-[11px] font-medium text-navy">{s.position}. {s.title}</span></div></div>;
        })}
        {today >= 0 && today <= 100 && <div className="pointer-events-none absolute inset-y-0 w-px bg-danger" style={{ left: `${today}%` }} />}
      </div>
    </div>
  );
}

export default function StagesPage() {
  const { t, locale } = useI18n();
  const { project, projectId } = useProject();
  const q = useStages();
  const [comment, setComment] = useState<Stage | null>(null);
  const [adding, setAdding] = useState(false);
  const role = project?.myRole;
  const canWork = ['CONTRACTOR', 'PROJECT_MANAGER', 'MASTER', 'ADMIN'].includes(role ?? '');
  const canAccept = ['OWNER', 'INSPECTOR', 'ADMIN'].includes(role ?? '');
  const patch = useAction((v: { id: string; body: Record<string, unknown> }) => api.patch<Stage>(`/api/v1/stages/${v.id}`, v.body), { success: 'stages.updated', invalidate: ['stages', 'projects'] });
  const [title, setTitle] = useState('');
  const create = useAction((b: { title: string }) => api.post(`/api/v1/projects/${projectId}/stages`, b), { success: 'stages.added', invalidate: ['stages'], onDone: () => { setAdding(false); setTitle(''); } });
  if (!projectId) return <EmptyState title={t('dash.noProject')} />;
  const set = (s: Stage, status: StageStatus) => patch.mutate({ id: s.id, body: { status } });
  return (
    <>
      <PageHeader title={t('nav.stages')} subtitle={t('stages.weighted')} actions={canWork && <Button variant="outline" onClick={() => setAdding(true)}><Plus className="h-4 w-4" />{t('stages.add')}</Button>} />
      {q.isLoading ? <Skeleton className="h-64" /> : q.isError ? <ErrorState error={q.error} onRetry={() => q.refetch()} /> : !q.data?.length ? <EmptyState title={t('stages.empty')} text={t('stages.emptyHint')} /> : (
        <>
          <Card className="mb-4"><Timeline stages={q.data} /></Card>
          <div className="space-y-3">
            {q.data.map((s) => (
              <Card key={s.id} className="p-4">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="flex items-start gap-3"><span className="grid h-8 w-8 place-items-center rounded-full bg-navy text-sm font-bold text-white">{s.position}</span>
                    <div><h3 className="font-semibold">{s.title}</h3><p className="text-xs text-ink-soft">{fmtDate(s.startDate, locale)} → {fmtDate(s.endDate, locale)}{s.actualEndDate && ` · ${t('stages.actualEnd')}: ${fmtDate(s.actualEndDate, locale)}`} · {t('stages.weight')} {s.weight}% · {fmtMoney(s.budget)}</p></div></div>
                  <div className="flex items-center gap-2">{s.dependsOn && <Badge>{t('stages.dependent')}</Badge>}<StatusBadge status={s.status} /></div>
                </div>
                <div className="mt-3 flex items-center gap-3"><Progress value={s.progress} tone={s.status === 'COMPLETED' ? 'success' : 'brand'} /><span className="tabular w-12 text-right text-sm font-semibold">{s.progress}%</span></div>
                <div className="mt-3 flex flex-wrap items-center gap-2">
                  {canWork && !['COMPLETED', 'UNDER_REVIEW'].includes(s.status) && (
                    <label className="flex items-center gap-2 text-xs text-ink-soft">{t('stages.progress')}
                      <input type="range" min={0} max={100} step={5} defaultValue={s.progress} key={`${s.id}-${s.progress}`} aria-label={`${s.title}: ${t('stages.progress')}`}
                        onMouseUp={(e) => patch.mutate({ id: s.id, body: { progress: Number((e.target as HTMLInputElement).value) } })} onTouchEnd={(e) => patch.mutate({ id: s.id, body: { progress: Number((e.target as HTMLInputElement).value) } })}
                        onKeyUp={(e) => patch.mutate({ id: s.id, body: { progress: Number((e.target as HTMLInputElement).value) } })} /></label>)}
                  {canWork && ['IN_PROGRESS', 'DELAYED'].includes(s.status) && <Button size="sm" variant="outline" onClick={() => set(s, 'UNDER_REVIEW')}>{t('stages.requestReview')}</Button>}
                  {canWork && s.status === 'NOT_STARTED' && <Button size="sm" variant="outline" onClick={() => set(s, 'IN_PROGRESS')}>{t('stages.start')}</Button>}
                  {canAccept && s.status === 'UNDER_REVIEW' && <><Button size="sm" variant="success" onClick={() => set(s, 'COMPLETED')}>{t('stages.accept')}</Button><Button size="sm" variant="outline" onClick={() => set(s, 'IN_PROGRESS')}>{t('stages.return')}</Button></>}
                  <Button size="sm" variant="ghost" onClick={() => setComment(s)}><MessageSquare className="h-4 w-4" />{t('stages.comments')}</Button>
                </div>
              </Card>
            ))}
          </div>
        </>
      )}
      <Dialog open={!!comment} onOpenChange={(o) => !o && setComment(null)}>{comment && <Comments stage={comment} onClose={() => setComment(null)} />}</Dialog>
      <Dialog open={adding} onOpenChange={setAdding}><DialogContent title={t('stages.add')}><Field label={t('stages.title')}><Input value={title} onChange={(e) => setTitle(e.target.value)} /></Field>
        <div className="mt-4 flex justify-end gap-2"><Button variant="ghost" onClick={() => setAdding(false)}>{t('common.cancel')}</Button><Button disabled={title.trim().length < 2} loading={create.isPending} onClick={() => create.mutate({ title })}>{t('common.save')}</Button></div></DialogContent></Dialog>
    </>
  );
}
