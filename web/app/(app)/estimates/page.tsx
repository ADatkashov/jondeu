'use client';
import { Download, FilePlus2, History } from 'lucide-react';
import { useEffect, useState } from 'react';
import { StatusBadge } from '@/components/Status';
import { Badge, Button, Card, CardTitle, Dialog, DialogContent, EmptyState, ErrorState, Field, PageHeader, Skeleton, Textarea } from '@/components/ui';
import { EstimateEditor } from '@/features/EstimateEditor';
import { api, request } from '@/lib/api';
import { fmtDate, fmtMoneyPrecise } from '@/lib/format';
import { useAction, useEstimates } from '@/lib/hooks';
import { useI18n } from '@/lib/i18n';
import { useProject } from '@/lib/project';
import { useToast } from '@/lib/toast';
import type { Estimate } from '@/lib/types';

function Comment({ title, onSubmit, onClose }: { title: string; onSubmit: (c: string) => void; onClose: () => void }) {
  const { t } = useI18n();
  const [c, setC] = useState('');
  return (<DialogContent title={title}><Field label={t('estimates.comment')}><Textarea value={c} onChange={(e) => setC(e.target.value)} /></Field>
    <div className="mt-4 flex justify-end gap-2"><Button variant="ghost" onClick={onClose}>{t('common.cancel')}</Button><Button onClick={() => { onSubmit(c); onClose(); }}>{t('common.send')}</Button></div></DialogContent>);
}

export default function EstimatesPage() {
  const { t, locale } = useI18n();
  const { project, projectId } = useProject();
  const q = useEstimates();
  const toast = useToast();
  const [sel, setSel] = useState<string>();
  const [editor, setEditor] = useState<{ est?: Estimate } | null>(null);
  const [comment, setComment] = useState<null | 'reject' | 'changes'>(null);
  useEffect(() => { if (q.data?.length && !q.data.some((e) => e.id === sel)) setSel((q.data.find((e) => e.status === 'APPROVED') ?? q.data[0]).id); }, [q.data, sel]);
  const est = q.data?.find((e) => e.id === sel);
  const role = project?.myRole;
  const canEdit = role === 'CONTRACTOR' || role === 'PROJECT_MANAGER' || role === 'ADMIN';
  const canApprove = role === 'OWNER' || role === 'ADMIN';
  const mk = (path: (e: Estimate) => string, success: string) => useAction((arg: { e: Estimate; body?: unknown }) => api.post<Estimate>(path(arg.e), arg.body), { success, invalidate: ['estimates', 'stages', 'payments', 'projects'] });
  const submit = mk((e) => `/api/v1/estimates/${e.id}/submit`, 'estimates.submitted');
  const approve = mk((e) => `/api/v1/estimates/${e.id}/approve`, 'estimates.approved');
  const reject = mk((e) => `/api/v1/estimates/${e.id}/reject`, 'estimates.rejected');
  const changes = mk((e) => `/api/v1/estimates/${e.id}/request-changes`, 'estimates.changesRequested');
  const revise = mk((e) => `/api/v1/estimates/${e.id}/revise`, 'estimates.revised');
  const pdf = async (e: Estimate) => {
    try {
      const blob = await request<Blob>(`/api/v1/estimates/${e.id}/pdf`);
      const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = `${e.number}-v${e.version}.pdf`; a.click(); URL.revokeObjectURL(a.href);
    } catch { toast(t('common.error'), 'error'); }
  };
  if (!projectId) return <EmptyState title={t('dash.noProject')} />;
  return (
    <>
      <PageHeader title={t('nav.estimates')} subtitle={project?.name} actions={canEdit && project && ['CONTRACTOR_SELECTED', 'IN_PROGRESS'].includes(project.status) && <Button onClick={() => setEditor({})}><FilePlus2 className="h-4 w-4" />{t('estimates.new')}</Button>} />
      {q.isLoading ? <Skeleton className="h-64" /> : q.isError ? <ErrorState error={q.error} onRetry={() => q.refetch()} /> : !q.data?.length ? <EmptyState title={t('estimates.empty')} text={t('estimates.emptyHint')} /> : (
        <div className="grid gap-4 xl:grid-cols-[320px_1fr]">
          <Card><CardTitle><span className="flex items-center gap-2"><History className="h-4 w-4" />{t('estimates.history')}</span></CardTitle>
            <ul className="space-y-1">{q.data.map((e) => (
              <li key={e.id}><button onClick={() => setSel(e.id)} aria-current={e.id === sel} className={`w-full rounded-control p-3 text-left text-sm transition ${e.id === sel ? 'bg-brand-50' : 'hover:bg-canvas'}`}>
                <div className="flex items-center justify-between"><b>{e.number} · v{e.version}</b><StatusBadge status={e.status} /></div>
                <div className="tabular mt-1 text-ink-soft">{fmtMoneyPrecise(e.total, locale)}</div><div className="text-xs text-ink-soft">{fmtDate(e.updatedAt, locale)}</div></button></li>
            ))}</ul></Card>
          {est && (
            <Card>
              <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
                <div><h2 className="text-lg font-bold">{est.number} · v{est.version}</h2><div className="mt-1 flex items-center gap-2"><StatusBadge status={est.status} />{est.status === 'APPROVED' && <Badge tone="navy">{t('estimates.locked')}</Badge>}</div></div>
                <div className="flex flex-wrap gap-2">
                  <Button variant="outline" size="sm" onClick={() => pdf(est)}><Download className="h-4 w-4" />PDF</Button>
                  {canEdit && ['DRAFT', 'CHANGES_REQUESTED', 'REJECTED'].includes(est.status) && <Button size="sm" variant="outline" onClick={() => setEditor({ est })}>{t('common.edit')}</Button>}
                  {canEdit && est.status === 'DRAFT' && <Button size="sm" loading={submit.isPending} onClick={() => submit.mutate({ e: est })}>{t('estimates.submit')}</Button>}
                  {canEdit && est.status === 'APPROVED' && <Button size="sm" variant="accent" onClick={() => revise.mutate({ e: est })}>{t('estimates.revise')}</Button>}
                  {canApprove && est.status === 'SUBMITTED' && <><Button size="sm" variant="success" loading={approve.isPending} onClick={() => approve.mutate({ e: est })}>{t('estimates.approve')}</Button>
                    <Button size="sm" variant="outline" onClick={() => setComment('changes')}>{t('estimates.requestChanges')}</Button><Button size="sm" variant="danger" onClick={() => setComment('reject')}>{t('estimates.reject')}</Button></>}
                </div>
              </div>
              {est.reviewComment && <p className="mb-3 rounded-control bg-warning-50 p-3 text-sm"><b>{t('estimates.comment')}:</b> {est.reviewComment}</p>}
              <div className="overflow-x-auto"><table className="w-full min-w-[640px] text-sm">
                <thead><tr className="border-b border-line text-left text-xs text-ink-soft"><th className="py-2">#</th><th>{t('estimates.work')}</th><th>{t('estimates.kind')}</th><th className="text-right">{t('estimates.qty')}</th><th className="text-right">{t('estimates.price')}</th><th className="text-right">{t('estimates.sum')}</th></tr></thead>
                <tbody className="divide-y divide-line">{est.items.map((i) => <tr key={i.id}><td className="py-2 text-ink-soft">{i.position}</td><td className="font-medium">{i.title}</td><td><Badge tone={i.kind === 'MATERIAL' ? 'orange' : 'blue'}>{t(`cost.${i.kind}`)}</Badge></td>
                  <td className="tabular text-right">{i.quantity} {i.unit}</td><td className="tabular text-right">{fmtMoneyPrecise(i.unitPrice, locale)}</td><td className="tabular text-right font-semibold">{fmtMoneyPrecise(i.total, locale)}</td></tr>)}</tbody></table></div>
              <dl className="mt-4 ml-auto grid max-w-sm grid-cols-[1fr_auto] gap-x-6 gap-y-1.5 text-sm">
                <dt className="text-ink-soft">{t('estimates.subtotal')}</dt><dd className="tabular text-right">{fmtMoneyPrecise(est.subtotal, locale)}</dd>
                <dt className="text-ink-soft">{t('estimates.additional')}</dt><dd className="tabular text-right">{fmtMoneyPrecise(est.additionalCosts, locale)}</dd>
                <dt className="text-ink-soft">{t('estimates.discount')}</dt><dd className="tabular text-right">−{fmtMoneyPrecise(est.discount, locale)}</dd>
                <dt className="text-ink-soft">{t('estimates.tax')} ({est.taxRate}%)</dt><dd className="tabular text-right">{fmtMoneyPrecise(est.taxAmount, locale)}</dd>
                <dt className="border-t border-line pt-2 font-bold">{t('estimates.total')}</dt><dd className="tabular border-t border-line pt-2 text-right text-lg font-bold">{fmtMoneyPrecise(est.total, locale)}</dd>
                {est.status === 'APPROVED' && <><dt className="text-ink-soft">{t('estimates.paid')}</dt><dd className="tabular text-right text-success">{fmtMoneyPrecise(est.paid, locale)}</dd>
                  <dt className="font-semibold">{t('estimates.remaining')}</dt><dd className="tabular text-right font-semibold">{fmtMoneyPrecise(est.remaining, locale)}</dd></>}
              </dl>
            </Card>
          )}
        </div>
      )}
      <Dialog open={!!editor} onOpenChange={(o) => !o && setEditor(null)}>{editor && projectId && <EstimateEditor projectId={projectId} estimate={editor.est} onClose={() => setEditor(null)} />}</Dialog>
      <Dialog open={!!comment} onOpenChange={(o) => !o && setComment(null)}>{comment && est && <Comment title={t(comment === 'reject' ? 'estimates.reject' : 'estimates.requestChanges')} onClose={() => setComment(null)}
        onSubmit={(c) => (comment === 'reject' ? reject : changes).mutate({ e: est, body: { comment: c } })} />}</Dialog>
    </>
  );
}
