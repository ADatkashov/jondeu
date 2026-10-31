'use client';
import { Banknote, CreditCard, FlaskConical, Landmark, Plus } from 'lucide-react';
import { useMemo, useState } from 'react';
import { StatusBadge } from '@/components/Status';
import { Badge, Button, Card, CardTitle, Dialog, DialogContent, EmptyState, ErrorState, Field, Input, PageHeader, Progress, Select, Skeleton, StatCard } from '@/components/ui';
import { api } from '@/lib/api';
import { fmtDateTime, fmtMoney, fmtMoneyPrecise } from '@/lib/format';
import { useAction, useOverview, usePayments } from '@/lib/hooks';
import { useI18n } from '@/lib/i18n';
import { useProject } from '@/lib/project';
import type { Payment, PaymentMethod, PaymentType } from '@/lib/types';
import { cn } from '@/lib/utils';

const METHODS: { m: PaymentMethod; icon: typeof CreditCard }[] = [{ m: 'CARD', icon: CreditCard }, { m: 'KASPI', icon: CreditCard }, { m: 'BANK_TRANSFER', icon: Landmark }, { m: 'CASH', icon: Banknote }];
const TYPES: PaymentType[] = ['ADVANCE', 'STAGE', 'MATERIAL', 'FINAL', 'ADDITIONAL'];

function PayDialog({ projectId, max, existing, onClose }: { projectId: string; max: number; existing?: Payment; onClose: () => void }) {
  const { t } = useI18n();
  // one idempotency key per dialog instance: a double click / network retry can never create two payments
  const key = useMemo(() => crypto.randomUUID(), []);
  const [type, setType] = useState<PaymentType>(existing?.type ?? 'STAGE');
  const [method, setMethod] = useState<PaymentMethod>('BANK_TRANSFER');
  const [amount, setAmount] = useState(existing ? String(existing.amount) : '');
  const n = Number(amount);
  const valid = n > 0 && n <= max;
  const pay = useAction(() => existing ? api.post(`/api/v1/payments/${existing.id}/pay`, { method }) : api.post(`/api/v1/projects/${projectId}/payments`, { type, method, amount: n }, { 'Idempotency-Key': key }),
    { success: 'pay.created', invalidate: ['payments'], onDone: onClose });
  return (
    <DialogContent title={existing ? t('pay.payRequested') : t('pay.new')}>
      <div className="rounded-card bg-navy p-4 text-white"><div className="text-xs text-white/70">{t('pay.toPay')}</div><div className="tabular text-3xl font-bold">{amount ? fmtMoney(n) : '—'}</div><div className="mt-1 text-xs text-white/70">{t('pay.available')}: {fmtMoney(max)}</div></div>
      <div className="mt-4 grid gap-3 sm:grid-cols-2">
        {!existing && <Field label={t('pay.type')}><Select value={type} onChange={(e) => setType(e.target.value as PaymentType)}>{TYPES.map((x) => <option key={x} value={x}>{t(`ptype.${x}`)}</option>)}</Select></Field>}
        {!existing && <Field label={t('pay.amount')} error={amount && !valid ? (n > max ? 'pay.exceeds' : 'val.positive') : undefined}><Input type="number" min={1} step="1000" value={amount} onChange={(e) => setAmount(e.target.value)} inputMode="numeric" /></Field>}
      </div>
      <fieldset className="mt-4"><legend className="mb-2 text-xs font-semibold text-ink-soft">{t('pay.method')}</legend>
        <div className="grid grid-cols-2 gap-2">{METHODS.map(({ m, icon: I }) => (
          <label key={m} className={cn('flex cursor-pointer items-center gap-2 rounded-control border p-3 text-sm', method === m ? 'border-brand bg-brand-50' : 'border-line')}><input type="radio" name="m" checked={method === m} onChange={() => setMethod(m)} className="accent-brand" /><I className="h-4 w-4" />{t(`method.${m}`)}</label>))}</div></fieldset>
      {(method === 'CARD' || method === 'KASPI') && <p className="mt-3 flex items-start gap-2 rounded-control bg-warning-50 p-3 text-xs"><FlaskConical className="mt-0.5 h-4 w-4 shrink-0 text-warning" />{t('pay.sandboxNote')}</p>}
      {(method === 'CASH' || method === 'BANK_TRANSFER') && <p className="mt-3 rounded-control bg-canvas p-3 text-xs text-ink-soft">{t('pay.manualNote')}</p>}
      <div className="mt-4 flex justify-end gap-2"><Button variant="ghost" onClick={onClose}>{t('common.cancel')}</Button><Button size="lg" disabled={!existing && !valid} loading={pay.isPending} onClick={() => pay.mutate(undefined)}>{t('pay.confirm')}</Button></div>
    </DialogContent>
  );
}

function RequestDialog({ projectId, max, onClose }: { projectId: string; max: number; onClose: () => void }) {
  const { t } = useI18n();
  const [type, setType] = useState<PaymentType>('STAGE');
  const [amount, setAmount] = useState('');
  const [note, setNote] = useState('');
  const n = Number(amount);
  const go = useAction(() => api.post(`/api/v1/projects/${projectId}/payment-requests`, { type, amount: n, note }), { success: 'pay.requestedOk', invalidate: ['payments'], onDone: onClose });
  return (<DialogContent title={t('pay.request')}><div className="grid gap-3 sm:grid-cols-2">
    <Field label={t('pay.type')}><Select value={type} onChange={(e) => setType(e.target.value as PaymentType)}>{TYPES.map((x) => <option key={x} value={x}>{t(`ptype.${x}`)}</option>)}</Select></Field>
    <Field label={t('pay.amount')} error={amount && n > max ? 'pay.exceeds' : undefined}><Input type="number" value={amount} onChange={(e) => setAmount(e.target.value)} /></Field>
    <div className="sm:col-span-2"><Field label={t('pay.note')}><Input value={note} onChange={(e) => setNote(e.target.value)} /></Field></div></div>
    <div className="mt-4 flex justify-end gap-2"><Button variant="ghost" onClick={onClose}>{t('common.cancel')}</Button><Button disabled={!(n > 0 && n <= max)} loading={go.isPending} onClick={() => go.mutate(undefined)}>{t('pay.request')}</Button></div></DialogContent>);
}

export default function PaymentsPage() {
  const { t, locale } = useI18n();
  const { project, projectId } = useProject();
  const ov = useOverview();
  const q = usePayments();
  const [dlg, setDlg] = useState<null | { pay?: Payment } | 'request'>(null);
  const role = project?.myRole;
  const isOwner = role === 'OWNER';
  const isPro = ['CONTRACTOR', 'PROJECT_MANAGER', 'ADMIN'].includes(role ?? '');
  const act = (path: (p: Payment) => string, success: string, body?: unknown) => useAction((p: Payment) => api.post(path(p), body), { success, invalidate: ['payments'] });
  const confirmM = act((p) => `/api/v1/payments/${p.id}/confirm`, 'pay.confirmed');
  const cancel = act((p) => `/api/v1/payments/${p.id}/cancel`, 'pay.cancelled');
  const refund = act((p) => `/api/v1/payments/${p.id}/refund`, 'pay.refunded');
  const sbOk = act((p) => `/api/v1/payments/${p.id}/sandbox-resolve`, 'pay.confirmed', { success: true });
  const sbFail = act((p) => `/api/v1/payments/${p.id}/sandbox-resolve`, 'pay.failed', { success: false });
  if (!projectId) return <EmptyState title={t('dash.noProject')} />;
  const f = ov.data?.finance;
  const available = f ? Math.max(0, f.approvedBudget - f.paid - f.reserved) : 0;
  return (
    <>
      <PageHeader title={t('nav.payments')} subtitle={t('pay.subtitle')} actions={<>{isOwner && f && f.approvedBudget > 0 && <Button onClick={() => setDlg({})}><Plus className="h-4 w-4" />{t('pay.new')}</Button>}
        {isPro && f && f.approvedBudget > 0 && <Button variant="accent" onClick={() => setDlg('request')}>{t('pay.request')}</Button>}</>} />
      {f && <div className="mb-4 grid grid-cols-2 gap-3 lg:grid-cols-4"><StatCard label={t('budget.approved')} value={fmtMoney(f.approvedBudget)} /><StatCard label={t('budget.spent')} value={fmtMoney(f.paid)} sub={f.reserved > 0 ? t('kpi.reserved', { v: fmtMoney(f.reserved) }) : undefined} />
        <StatCard label={t('budget.remaining')} value={fmtMoney(f.remaining)} tone="green" /><StatCard label={t('pay.requested')} value={fmtMoney(f.pendingRequests)} tone={f.pendingRequests > 0 ? 'orange' : undefined} /></div>}
      {f && f.approvedBudget > 0 && <Card className="mb-4"><div className="mb-2 flex justify-between text-sm"><span>{t('budget.used')}</span><b className="tabular">{f.utilizationPercent.toFixed(1)}%</b></div><Progress value={f.utilizationPercent} tone="success" className="h-3" /></Card>}
      {f && f.approvedBudget <= 0 && <EmptyState title={t('budget.none')} text={t('budget.noneHint')} />}
      <Card><CardTitle>{t('pay.history')}</CardTitle>
        {q.isLoading ? <Skeleton className="h-40" /> : q.isError ? <ErrorState error={q.error} onRetry={() => q.refetch()} /> : !q.data?.length ? <p className="py-6 text-center text-sm text-ink-soft">{t('pay.empty')}</p> : (
          <div className="overflow-x-auto"><table className="w-full min-w-[760px] text-sm"><thead><tr className="border-b border-line text-left text-xs text-ink-soft"><th className="py-2">{t('pay.reference')}</th><th>{t('pay.type')}</th><th>{t('pay.method')}</th><th className="text-right">{t('pay.amount')}</th><th>{t('common.status')}</th><th>{t('common.date')}</th><th /></tr></thead>
            <tbody className="divide-y divide-line">{q.data.map((p) => (
              <tr key={p.id}><td className="py-3"><b>{p.reference}</b>{p.note && <div className="text-xs text-ink-soft">{p.note}</div>}{p.sandbox && <Badge tone="yellow" className="mt-1"><FlaskConical className="h-3 w-3" />SANDBOX</Badge>}</td>
                <td>{t(`ptype.${p.type}`)}</td><td>{t(`method.${p.method}`)}</td><td className="tabular text-right font-semibold">{fmtMoneyPrecise(p.amount, locale)}</td><td><StatusBadge status={p.status} /></td><td className="text-xs text-ink-soft">{fmtDateTime(p.paidAt ?? p.createdAt, locale)}</td>
                <td className="py-3 text-right"><div className="flex flex-wrap justify-end gap-1">
                  {isOwner && p.status === 'REQUESTED' && <Button size="sm" onClick={() => setDlg({ pay: p })}>{t('pay.payNow')}</Button>}
                  {isPro && p.status === 'PENDING' && p.provider === 'MANUAL' && <Button size="sm" variant="success" loading={confirmM.isPending} onClick={() => confirmM.mutate(p)}>{t('pay.confirmReceipt')}</Button>}
                  {isOwner && p.sandbox && p.status === 'PENDING' && <><Button size="sm" variant="outline" onClick={() => sbOk.mutate(p)}>{t('pay.sandboxOk')}</Button><Button size="sm" variant="ghost" onClick={() => sbFail.mutate(p)}>{t('pay.sandboxFail')}</Button></>}
                  {['REQUESTED', 'PENDING'].includes(p.status) && (isOwner || isPro) && <Button size="sm" variant="ghost" onClick={() => cancel.mutate(p)}>{t('common.cancel')}</Button>}
                  {isPro && p.status === 'PAID' && <Button size="sm" variant="ghost" onClick={() => confirm(t('pay.confirmRefund')) && refund.mutate(p)}>{t('pay.refund')}</Button>}</div></td></tr>))}</tbody></table></div>)}
      </Card>
      <Dialog open={!!dlg} onOpenChange={(o) => !o && setDlg(null)}>
        {dlg === 'request' ? <RequestDialog projectId={projectId} max={available} onClose={() => setDlg(null)} /> : dlg && <PayDialog projectId={projectId} max={available} existing={dlg.pay} onClose={() => setDlg(null)} />}</Dialog>
    </>
  );
}
