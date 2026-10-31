'use client';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { StatusBadge } from '@/components/Status';
import { Badge, Button, Card, Dialog, DialogContent, EmptyState, ErrorState, Field, Input, PageHeader, Skeleton, Textarea } from '@/components/ui';
import { api } from '@/lib/api';
import { fmtMoney } from '@/lib/format';
import { useAction, useCategories } from '@/lib/hooks';
import { useI18n } from '@/lib/i18n';
import type { OpenRequest, Page, Proposal } from '@/lib/types';

function Bid({ r, onClose }: { r: OpenRequest; onClose: () => void }) {
  const { t } = useI18n();
  const [v, setV] = useState({ price: '', durationDays: '', message: '' });
  const go = useAction(() => api.post(`/api/v1/projects/${r.id}/proposals`, { price: Number(v.price), durationDays: Number(v.durationDays), message: v.message }), { success: 'requests.sent', invalidate: ['open-requests', 'my-proposals'], onDone: onClose });
  return (
    <DialogContent title={`${t('requests.bid')}: ${r.name}`}>
      <p className="mb-3 text-sm text-ink-soft">{r.description}</p>
      <div className="grid gap-3 sm:grid-cols-2"><Field label={t('proposals.price')}><Input type="number" value={v.price} onChange={(e) => setV({ ...v, price: e.target.value })} /></Field>
        <Field label={t('proposals.days')}><Input type="number" value={v.durationDays} onChange={(e) => setV({ ...v, durationDays: e.target.value })} /></Field>
        <div className="sm:col-span-2"><Field label={t('proposals.message')}><Textarea value={v.message} onChange={(e) => setV({ ...v, message: e.target.value })} /></Field></div></div>
      <div className="mt-4 flex justify-end gap-2"><Button variant="ghost" onClick={onClose}>{t('common.cancel')}</Button><Button disabled={!(Number(v.price) > 0 && Number(v.durationDays) > 0)} loading={go.isPending} onClick={() => go.mutate(undefined)}>{t('requests.send')}</Button></div>
    </DialogContent>
  );
}

export default function RequestsPage() {
  const { t, lang } = useI18n();
  const cats = useCategories();
  const [bid, setBid] = useState<OpenRequest | null>(null);
  const open = useQuery({ queryKey: ['open-requests'], queryFn: () => api.get<Page<OpenRequest>>('/api/v1/requests/open?size=30') });
  const mine = useQuery({ queryKey: ['my-proposals'], queryFn: () => api.get<Proposal[]>('/api/v1/proposals/mine') });
  const catName = (c?: string) => cats.data?.find((x) => x.code === c)?.[lang] ?? c;
  return (
    <>
      <PageHeader title={t('nav.requests')} subtitle={t('requests.subtitle')} />
      {open.isLoading ? <Skeleton className="h-48" /> : open.isError ? <ErrorState error={open.error} onRetry={() => open.refetch()} /> : !open.data?.content.length ? <EmptyState title={t('requests.empty')} /> : (
        <div className="grid gap-4 md:grid-cols-2">{open.data.content.map((r) => (
          <Card key={r.id}><div className="flex items-start justify-between gap-2"><h3 className="font-semibold">{r.name}</h3><StatusBadge status={r.status} /></div>
            <div className="mt-1 flex flex-wrap gap-2 text-xs"><Badge tone="blue">{catName(r.category)}</Badge><Badge>{r.areaM2} m²</Badge><Badge>{r.city}</Badge></div>
            <p className="mt-2 line-clamp-3 text-sm text-ink-soft">{r.description}</p>
            <p className="mt-2 text-sm">{t('projects.budgetRange')}: <b>{fmtMoney(r.budgetMin)} – {fmtMoney(r.budgetMax)}</b></p>
            <div className="mt-3 flex items-center justify-between"><span className="text-xs text-ink-soft">{t('requests.proposals', { n: r.proposalsCount })}</span>
              {r.myProposal ? <Badge tone="green">{t(`status.${r.myProposal}`)}</Badge> : <Button size="sm" onClick={() => setBid(r)}>{t('requests.bid')}</Button>}</div></Card>))}</div>
      )}
      <h2 className="mb-3 mt-8 text-lg font-bold">{t('requests.mine')}</h2>
      {!mine.data?.length ? <p className="text-sm text-ink-soft">{t('requests.noneMine')}</p> : <Card className="p-0"><ul className="divide-y divide-line">{mine.data.map((p) => <li key={p.id} className="flex items-center justify-between gap-3 p-4 text-sm"><span><b>{fmtMoney(p.price)}</b> · {p.durationDays} {t('proposals.daysShort')}</span><StatusBadge status={p.status} /></li>)}</ul></Card>}
      <Dialog open={!!bid} onOpenChange={(o) => !o && setBid(null)}>{bid && <Bid r={bid} onClose={() => setBid(null)} />}</Dialog>
    </>
  );
}
